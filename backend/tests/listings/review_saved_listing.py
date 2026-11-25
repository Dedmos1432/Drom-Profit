"""Replay an authorized saved HTML capture through the Drom parser without networking."""

import argparse
import asyncio
import json
from dataclasses import fields
from pathlib import Path
from typing import Any

from app.services.listings.drom_provider import DromHtmlProvider, listing_from_html
from app.services.listings.errors import IMPORT_ERROR_CODES, ListingImportError
from app.services.listings.importer import ListingImporter
from app.services.listings.models import ListingData
from app.services.listings.safe_http import HttpLimits, HttpResponse, RawResponse, SafeHttpClient
from app.services.listings.urls import HostAllowlist, validate_listing_url


def compare_field(field: str, actual: Any, reference: dict) -> dict:
    state = reference.get("state", "unverified")
    expected = reference.get("value")
    if state == "observed":
        if expected is None:
            raise ValueError(f"{field}: observed требует непустое значение")
        if actual is None:
            state = "missing"
        else:
            state = "match" if actual == expected else "mismatch"
    elif state == "absent":
        state = "absent" if actual is None else "mismatch"
    elif state not in {"unconfirmed", "unverified"}:
        raise ValueError(f"{field}: неверный статус эталона")
    return {
        "field": field,
        "expected": expected,
        "actual": actual,
        "status": state,
        "origin": reference.get("origin"),
    }


def review_sample(manifest_path: Path) -> dict:
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    kind = manifest["kind"]
    if kind not in {"synthetic", "saved_html"}:
        raise ValueError("kind должен быть synthetic или saved_html")
    allowed = HostAllowlist(manifest["allowed_hosts"])
    redirects = tuple(manifest.get("redirect_hosts", ()))
    capture_hosts = HostAllowlist((*allowed.entries, *redirects))
    limits = HttpLimits()
    document = manifest.get("document")
    if document is not None and "responses" in manifest:
        raise ValueError("Используйте document или responses")
    document_url = None
    frames = []
    for capture in manifest.get("responses", []):
        target = validate_listing_url(capture["url"], capture_hosts)
        status = capture["status"]
        if type(status) is not int or not 100 <= status <= 599:
            raise ValueError("status должен быть целым HTTP-статусом от 100 до 599")
        headers = {name.lower(): value for name, value in capture.get("headers", {}).items()}
        body = b""
        if status == 200:
            with (manifest_path.parent / capture["body_file"]).open("rb") as source:
                body = source.read(limits.max_bytes + 1)
        frames.append(HttpResponse(target, status, headers, body))
    if not frames and document is None:
        raise ValueError("Нужен сохранённый документ или как минимум один записанный HTTP-ответ")
    pending = iter(frames)
    replayed = []

    def transport(_address, host, path, _headers, _limits, _deadline):
        frame = next(pending, None)
        if frame is None or (frame.url.host, frame.url.path) != (host, path):
            raise RuntimeError("Сохранённые ответы не соответствуют последовательности запросов")
        replayed.append(frame)
        return RawResponse(frame.status, frame.headers, iter((frame.body,)))

    expected_error = manifest.get("expected_error")
    if expected_error is not None and expected_error not in IMPORT_ERROR_CODES:
        raise ValueError("Неизвестный expected_error")
    references = manifest.get("expected_fields", {})
    if references.keys() - {field.name for field in fields(ListingData)}:
        raise ValueError("expected_fields содержит неизвестное поле ListingData")

    error_code = None
    listing = None
    try:
        if document is not None:
            target = validate_listing_url(document["canonical_url"], allowed)
            document_url = target.canonical
            with (manifest_path.parent / document["body_file"]).open("rb") as source:
                body = source.read(limits.max_bytes + 1)
            if len(body) > limits.max_bytes:
                raise ValueError("Сохранённый документ превышает допустимый размер")
            listing = listing_from_html(body.decode(document["encoding"]), target)
        else:
            # The address satisfies the client's public-address predicate. DNS and
            # transport are supplied locally, so it is never contacted.
            client = SafeHttpClient(
                allowed, limits, resolver=lambda _host: ["1.1.1.1"], transport=transport,
                redirect_hosts=redirects,
            )
            provider = DromHtmlProvider(
                access_approved=True, allowed_hosts=allowed, client=client,
                redirect_hosts=HostAllowlist(redirects) if redirects else None,
            )
            listing = asyncio.run(ListingImporter(provider)(manifest["request_url"]))
    except ListingImportError as error:
        error_code = error.code

    rows = []
    if listing is not None:
        rows = [
            compare_field(name, value, references.get(name, {}))
            for name, value in listing.as_dict().items()
        ]
    return {
        "kind": kind,
        "captured_at": manifest.get("captured_at"),
        "document_url": document_url,
        "document_encoding": document["encoding"] if document is not None else None,
        "responses": [
            {
                "url": frame.url.canonical,
                "status": frame.status,
                "content_type": frame.headers.get("content-type"),
                "charset": frame.charset,
                "replayed": index < len(replayed),
            }
            for index, frame in enumerate(frames)
        ],
        "error": error_code,
        "expected_error": expected_error,
        "fields": rows,
        "ok": (
            error_code == expected_error
            and len(replayed) == len(frames)
            and not any(row["status"] in {"missing", "mismatch"} for row in rows)
        ),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path)
    args = parser.parse_args()
    try:
        report = review_sample(args.manifest)
    except (OSError, ValueError, TypeError, KeyError, AttributeError, RuntimeError, ListingImportError) as error:
        parser.error(str(error))
    print(json.dumps(report, indent=2, ensure_ascii=False))
    return 0 if report["ok"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
