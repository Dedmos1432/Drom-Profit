import json
from pathlib import Path

import pytest

from app.services.listings import drom_provider
from tests.listings.review_saved_listing import review_sample


HOST = "vladivostok.drom.ru"
URL = f"https://{HOST}/toyota/camry/12345678.html"
PAGE = Path(__file__).parent / "samples" / "synthetic_listing.html"


def make_manifest(tmp_path, **overrides):
    manifest = {
        "kind": "synthetic",
        "request_url": URL,
        "allowed_hosts": [HOST],
        "responses": [{
            "url": URL,
            "status": 200,
            "headers": {"Content-Type": "text/html; charset=utf-8"},
            "body_file": str(PAGE),
        }],
    } | overrides
    path = tmp_path / "capture.json"
    path.write_text(json.dumps(manifest), encoding="utf-8")
    return path


def test_reviews_saved_redirect_and_compares_the_fields(tmp_path, monkeypatch):
    def unexpected_worker(*args):
        pytest.fail("Saved captures must never use the live worker")

    monkeypatch.setattr(drom_provider, "get_html", unexpected_worker)
    alternate = URL.replace("/toyota/", "/used/toyota/")
    report = review_sample(make_manifest(
        tmp_path,
        request_url=alternate + "?ref=offline",
        responses=[
            {"url": alternate, "status": 302, "headers": {"Location": URL}},
            {
                "url": URL, "status": 200,
                "headers": {"Content-Type": "text/html; charset=utf-8"},
                "body_file": str(PAGE),
            },
        ],
        expected_fields={
            "price": {"state": "observed", "value": 1200000, "origin": "JSON-LD Offer.price"},
            "market_valuation": {"state": "absent", "origin": "synthetic page"},
            "fetched_at": {"state": "unconfirmed"},
        },
    ))
    assert report["ok"]
    assert report["kind"] == "synthetic"
    assert [capture["status"] for capture in report["responses"]] == [302, 200]
    assert all(capture["replayed"] for capture in report["responses"])
    assert report["responses"][-1]["charset"] == "utf-8"
    rows = {row["field"]: row for row in report["fields"]}
    assert len(rows) == 19
    assert rows["listing_url"]["actual"] == URL
    assert rows["price"] == {
        "field": "price", "expected": 1200000, "actual": 1200000,
        "status": "match", "origin": "JSON-LD Offer.price",
    }
    assert rows["market_valuation"]["status"] == "absent"
    assert rows["fetched_at"]["status"] == "unconfirmed"
    assert rows["image_url"]["status"] == "unverified"


def test_reports_missing_and_mismatched_values_separately(tmp_path):
    report = review_sample(make_manifest(tmp_path, expected_fields={
        "price": {"state": "observed", "value": 1250000},
        "market_valuation": {"state": "observed", "value": 1000000},
    }))
    assert not report["ok"]
    rows = {row["field"]: row for row in report["fields"]}
    assert rows["price"]["status"] == "mismatch"
    assert rows["market_valuation"]["status"] == "missing"


@pytest.mark.parametrize(("status", "headers", "error_code"), [
    (404, {}, "listing_unavailable"),
    (403, {}, "source_forbidden"),
    (503, {}, "source_unavailable"),
    (200, {"Content-Type": "application/json"}, "parse_error"),
])
def test_replays_errors_via_the_existing_http_client(tmp_path, status, headers, error_code):
    report = review_sample(make_manifest(
        tmp_path,
        responses=[{
            "url": URL, "status": status, "headers": headers, "body_file": str(PAGE),
        }],
        expected_error=error_code,
    ))
    assert report["ok"]
    assert report["error"] == error_code
    assert report["fields"] == []


def test_saved_document_uses_its_file_encoding_without_http_status(tmp_path):
    local_html = tmp_path / "document.html"
    local_html.write_bytes(PAGE.read_text("utf-8").encode("cp1251"))
    path = tmp_path / "capture.json"
    path.write_text(json.dumps({
        "kind": "synthetic",
        "allowed_hosts": [HOST],
        "document": {"canonical_url": URL, "body_file": local_html.name, "encoding": "cp1251"},
    }), encoding="utf-8")
    report = review_sample(path)
    assert report["ok"]
    assert report["responses"] == []
    assert report["document_url"] == URL
    assert report["document_encoding"] == "cp1251"
    rows = {row["field"]: row for row in report["fields"]}
    assert rows["city"]["actual"] == "Владивосток"
