"""Выполнение блокирующего HTTPS-запроса в процессе с ограниченным временем жизни."""

import json
import subprocess
import sys
import time
from dataclasses import asdict, replace
from pathlib import Path

from .errors import ListingImportError
from .safe_http import HttpLimits, HttpResponse, SafeHttpClient
from .urls import HostAllowlist, ListingUrl, validate_listing_url

_BACKEND_ROOT = Path(__file__).resolve().parents[3]
_WORKER_CODE = (
    "import sys; sys.path.insert(0, sys.argv[1]); "
    "from app.services.listings.http_worker import main; main()"
)


def get_html(
    target: ListingUrl,
    hosts: HostAllowlist,
    redirect_hosts: tuple[str, ...],
    limits: HttpLimits,
    deadline: float,
) -> HttpResponse:
    request = {
        "url": target.canonical,
        "hosts": hosts.entries,
        "redirect_hosts": redirect_hosts,
        "limits": asdict(limits),
        "deadline": deadline,
    }
    remaining = deadline - time.monotonic()
    if remaining <= 0:
        raise ListingImportError("source_unavailable", "общий таймаут")
    try:
        completed = subprocess.run(
            [sys.executable, "-I", "-S", "-B", "-c", _WORKER_CODE, str(_BACKEND_ROOT)],
            input=json.dumps(request).encode(),
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            timeout=remaining,
            check=True,
        )
    except subprocess.TimeoutExpired:
        # subprocess.run завершает и дожидается дочернего процесса, включая блокирующий DNS.
        raise ListingImportError("source_unavailable", "общий таймаут") from None
    except (OSError, subprocess.CalledProcessError):
        raise ListingImportError("source_unavailable", "сбой сетевого процесса") from None
    if time.monotonic() >= deadline:
        raise ListingImportError("source_unavailable", "общий таймаут")

    header, separator, body = completed.stdout.partition(b"\n")
    try:
        if not separator:
            raise ValueError("нет ответа процесса")
        result = json.loads(header)
        if "code" in result:
            raise ListingImportError(result["code"], result.get("detail"))
        final = validate_listing_url(
            result["url"], HostAllowlist((*hosts.entries, *redirect_hosts))
        )
        return HttpResponse(final, result["status"], result["headers"], body)
    except (ValueError, KeyError, TypeError):
        raise ListingImportError("source_unavailable", "некорректный ответ сетевого процесса") from None


def main() -> None:
    request = json.load(sys.stdin.buffer)
    body = b""
    try:
        remaining = request["deadline"] - time.monotonic()
        if remaining <= 0:
            raise ListingImportError("source_unavailable", "общий таймаут")
        limits = replace(HttpLimits(**request["limits"]), total_timeout=remaining)
        client = SafeHttpClient(
            HostAllowlist(request["hosts"]), limits, redirect_hosts=tuple(request["redirect_hosts"])
        )
        response = client.get_html(request["url"])
        result = {"url": response.url.canonical, "status": response.status, "headers": response.headers}
        body = response.body
    except ListingImportError as error:
        result = {"code": error.code, "detail": error.detail}
    sys.stdout.buffer.write(json.dumps(result).encode() + b"\n" + body)
