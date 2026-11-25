import asyncio
import json
from concurrent.futures import ThreadPoolExecutor
from threading import Event

import pytest
from fastapi.testclient import TestClient

from app import schemas
from app.main import create_app
from app.services import listing_import
from app.services.listings import DEFAULT_FIXTURE_PATH
from app.services.listings import ListingData as ParserListingData
from app.services.listings import ListingImportError as ParserImportError


PREVIEW = "/api/cars/import"
CARS = "/api/cars"
DEMO_ROWS = json.loads(DEFAULT_FIXTURE_PATH.read_text("utf-8"))
DEMO_URL = DEMO_ROWS[0]["listing_url"]


def session_headers(client):
    response = client.post("/api/session")
    assert response.status_code == 201
    return {"Authorization": "Bearer " + response.json()["token"]}


def assert_error(response, status, code):
    assert response.status_code == status, response.text
    body = response.json()
    assert set(body) == {"code", "message"}
    assert body["code"] == code
    assert isinstance(body["message"], str) and body["message"]


@pytest.mark.parametrize("expected", DEMO_ROWS, ids=lambda row: row["listing_id"])
def test_default_demo_preview_and_edited_save(client, headers, new_session, expected):
    assert client.get(CARS, headers=headers).json() == []
    response = client.post(PREVIEW, headers=headers, json={"url": expected["listing_url"]})
    assert response.status_code == 200
    preview = response.json()
    required = client.app.openapi()["components"]["schemas"]["ListingData"]["required"]
    assert len(required) == 18
    assert set(preview) == set(required)
    assert preview == expected
    assert client.get(CARS, headers=headers).json() == []

    edited = preview | {"price": expected["price"] + 1000, "trim": "Уточнено при осмотре"}
    saved = client.post(CARS, headers=headers, json=edited)
    assert saved.status_code == 201, saved.text
    car = saved.json()
    assert {field: car[field] for field in required} == edited
    path = f"{CARS}/{car['id']}"
    assert client.get(path, headers=headers).json() == car
    guest = new_session()
    assert client.get(CARS, headers=guest).json() == []
    assert_error(client.get(path, headers=guest), 404, "not_found")


@pytest.mark.parametrize(("url", "status", "code"), [
    (DEMO_URL, 200, None),
    (DEMO_URL + "?ref=demo", 200, None),
    (DEMO_URL + "#photo", 200, None),
    (DEMO_URL.replace("https://", "http://"), 400, "invalid_url"),
    (DEMO_URL.replace("auto.drom.ru", "AUTO.DROM.RU"), 200, None),
    (DEMO_URL.replace("auto.drom.ru", "auto.drom.ru:443"), 400, "invalid_url"),
    ("https://drom.ru/demo/1.html", 400, "invalid_url"),
    ("https://auto.drom.ru/", 400, "invalid_url"),
    ("https://auto.drom.ru/demo/", 400, "invalid_url"),
    (DEMO_URL.removesuffix(".html"), 400, "invalid_url"),
    (DEMO_URL.replace("auto.drom.ru", "example.invalid"), 400, "invalid_url"),
    ("https://auto.drom.ru/demo/999.html", 404, "listing_unavailable"),
], ids=[
    "exact", "query", "fragment", "http", "uppercase-host", "port-443", "root-domain",
    "home", "catalog", "no-html", "third-party", "unknown-demo",
])
def test_demo_url_matrix(client, headers, url, status, code):
    response = client.post(PREVIEW, headers=headers, json={"url": url})
    if code is None:
        assert response.status_code == status, response.text
        assert response.json()["listing_url"] == DEMO_URL
    else:
        assert_error(response, status, code)


@pytest.mark.parametrize("headers", [{}, {"Authorization": "Bearer invalid"}])
def test_preview_requires_bearer(client, headers):
    response = client.post(PREVIEW, headers=headers, json={"url": DEMO_URL})
    assert_error(response, 401, "unauthorized")
    assert response.headers["www-authenticate"] == "Bearer"


@pytest.mark.parametrize("body", [{}, {"url": 42}])
def test_preview_rejects_wrong_request_shape(client, headers, body):
    assert_error(client.post(PREVIEW, headers=headers, json=body), 422, "validation_error")


@pytest.mark.parametrize(("code", "status"), [
    ("invalid_url", 400),
    ("listing_unavailable", 404),
    ("source_forbidden", 403),
    ("parse_error", 502),
    ("source_unavailable", 503),
])
def test_parser_error_translation(db_url, monkeypatch, code, status):
    async def parser(_url):
        raise ParserImportError(code, "private parser detail")

    monkeypatch.setattr(listing_import, "importer_from_env", lambda: parser)
    with TestClient(create_app(db_url)) as client:
        response = client.post(PREVIEW, headers=session_headers(client), json={"url": DEMO_URL})
    assert_error(response, status, code)
    assert "private parser detail" not in response.text


@pytest.mark.parametrize(
    "data", [None, ParserListingData(source="demo", image_url="https://example.invalid/invalid URI")],
    ids=["wrong-result-type", "invalid-core-field"],
)
def test_invalid_parser_result_is_502(db_url, monkeypatch, data):
    async def parser(_url):
        return data

    monkeypatch.setattr(listing_import, "importer_from_env", lambda: parser)
    with TestClient(create_app(db_url)) as client:
        response = client.post(PREVIEW, headers=session_headers(client), json={"url": DEMO_URL})
    assert_error(response, 502, "parse_error")
    assert "invalid URI" not in response.text


def test_unexpected_parser_exception_remains_internal_error(db_url, monkeypatch):
    async def parser(_url):
        raise RuntimeError("private parser detail")

    monkeypatch.setattr(listing_import, "importer_from_env", lambda: parser)
    with TestClient(create_app(db_url), raise_server_exceptions=False) as client:
        response = client.post(PREVIEW, headers=session_headers(client), json={"url": DEMO_URL})
    assert_error(response, 500, "internal_error")
    assert "private parser detail" not in response.text


def test_missing_preview_price_must_be_filled_before_save(db_url):
    async def parser(_url):
        return ParserListingData(source="demo", brand="Demo", model="Test", market_valuation=0)

    with TestClient(create_app(db_url, importer=listing_import.adapt_parser(parser))) as client:
        headers = session_headers(client)
        preview = client.post(PREVIEW, headers=headers, json={"url": DEMO_URL})
        assert preview.status_code == 200
        data = preview.json()
        assert data["price"] is None and data["market_valuation"] is None
        assert_error(client.post(CARS, headers=headers, json=data), 422, "validation_error")
        assert client.get(CARS, headers=headers).json() == []
        saved = client.post(CARS, headers=headers, json=data | {"price": 10})
        assert saved.status_code == 201 and saved.json()["price"] == 10


def test_parser_created_once_per_application(monkeypatch):
    monkeypatch.delenv("DROM_LISTINGS_SOURCE", raising=False)
    original_factory = listing_import.importer_from_env
    importers = []

    def create_parser():
        parser = original_factory()
        importers.append(parser)
        return parser

    monkeypatch.setattr(listing_import, "importer_from_env", create_parser)
    importer = create_app().state.import_listing

    async def import_twice():
        return await asyncio.gather(importer(DEMO_URL), importer(DEMO_ROWS[1]["listing_url"]))

    previews = asyncio.run(import_twice())
    assert len(importers) == 1
    assert importers[0].source == "demo"
    assert all(isinstance(preview, schemas.ListingData) for preview in previews)
    assert [preview.model_dump() for preview in previews] == DEMO_ROWS[:2]


@pytest.mark.parametrize("source", ["invalid", "drom"])
def test_unsupported_source_fails_at_app_creation(monkeypatch, source):
    monkeypatch.setenv("DROM_LISTINGS_SOURCE", source)
    with pytest.raises(ValueError, match="DROM_LISTINGS_SOURCE"):
        create_app()


def test_missing_demo_fixture_fails_at_app_creation(monkeypatch, tmp_path):
    monkeypatch.setenv("DROM_LISTINGS_FIXTURE", str(tmp_path / "missing.json"))
    with pytest.raises(FileNotFoundError):
        create_app()


def test_waiting_import_does_not_hold_pg_connection(db_url):
    started = Event()
    release = Event()
    importer = listing_import.make_parser_importer()

    async def waiting_import(url):
        started.set()
        assert await asyncio.to_thread(release.wait, 10)
        return await importer(url)

    app = create_app(db_url, importer=waiting_import)
    with TestClient(app) as client, ThreadPoolExecutor(max_workers=1) as executor:
        headers = session_headers(client)
        pending = executor.submit(client.post, PREVIEW, headers=headers, json={"url": DEMO_URL})
        try:
            assert started.wait(5)
            assert not pending.done()
            assert app.state.engine.pool.checkedout() == 0
            assert client.get(CARS, headers=headers).json() == []
        finally:
            release.set()
        assert pending.result(timeout=5).status_code == 200



def test_saved_drom_document_through_core_adapter(db_url):
    from pathlib import Path

    from app.services.listing_import import adapt_parser
    from app.services.listings.drom_provider import listing_from_html
    from app.services.listings.urls import HostAllowlist, validate_listing_url

    url = "https://vladivostok.drom.ru/toyota/camry/12345678.html"
    hosts = HostAllowlist(("vladivostok.drom.ru",))
    html = (Path(__file__).parent / "listings/samples/synthetic_listing.html").read_text("utf-8")

    async def parse_saved_document(requested_url):
        return listing_from_html(html, validate_listing_url(requested_url, hosts))

    with TestClient(create_app(db_url, importer=adapt_parser(parse_saved_document))) as client:
        headers = session_headers(client)
        preview = client.post(PREVIEW, headers=headers, json={"url": url})
        assert preview.status_code == 200
        listing = preview.json()
        assert len(listing) == 18
        assert (listing["source"], listing["listing_id"], listing["price"]) == (
            "drom", "12345678", 1200000,
        )
        assert client.get(CARS, headers=headers).json() == []
        saved = client.post(CARS, headers=headers, json=listing)
        assert saved.status_code == 201
        assert saved.json()["listing_url"] == url


def test_import_to_comparison_and_restart(db_url):
    with TestClient(create_app(db_url)) as client:
        headers = session_headers(client)
        previews = [
            client.post(PREVIEW, headers=headers, json={"url": row["listing_url"]})
            for row in DEMO_ROWS[:2]
        ]
        assert [response.status_code for response in previews] == [200, 200]
        # This synthetic URL exercises persistence without requiring an image request.
        image_url = "https://example.invalid/inspection.jpg"
        payloads = [previews[0].json() | {"image_url": image_url}, previews[1].json()]
        saved = [client.post(CARS, headers=headers, json=payload) for payload in payloads]
        assert [response.status_code for response in saved] == [201, 201]
        first, second = [response.json() for response in saved]
        path = f"{CARS}/{first['id']}"
        assert first["image_url"] == image_url
        assert second["image_url"] is None

        expense = {"title": "ТО", "kind": "maintenance", "amount": 10_000}
        benefit = {"title": "Комплект шин", "amount": 5_000}
        assert client.post(path + "/expenses", headers=headers, json=expense).status_code == 201
        assert client.post(path + "/benefits", headers=headers, json=benefit).status_code == 201
        flags = {"expenses_reviewed": True, "benefits_reviewed": True}
        for car in (first, second):
            assert client.patch(f"{CARS}/{car['id']}", headers=headers, json=flags).status_code == 200
        compare = {"car_ids": [first["id"], second["id"]]}

    with TestClient(create_app(db_url)) as client:
        cards = client.get(CARS, headers=headers)
        assert cards.status_code == 200
        by_id = {car["id"]: car for car in cards.json()}
        assert by_id[first["id"]]["image_url"] == image_url
        assert by_id[second["id"]]["image_url"] is None
        for key in ("source", "listing_id", "listing_url", "fetched_at"):
            assert by_id[first["id"]][key] == first[key]
        summary = client.get(path + "/summary", headers=headers)
        assert summary.status_code == 200
        assert summary.json()["acquisition_cost"] == 1_210_000
        assert summary.json()["adjusted_cost"] == 1_205_000
        assert summary.json()["assessment_status"] == "complete"
        response = client.post("/api/comparison", headers=headers, json=compare)
        assert response.status_code == 200
        result = response.json()
        assert result["provisional"] is False
        assert result["warnings"] == []
        assert [item["difference_from_min"] for item in result["items"]] == [0, 65_000]
