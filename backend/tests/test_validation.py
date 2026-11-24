import sqlite3

import pytest
from fastapi.testclient import TestClient

from app.main import create_app


def test_nonblank_fields_on_create_and_patch(client, headers, new_car):
    body = {"source": "manual", "brand": "Марка", "model": "Модель", "price": 100}
    car = new_car(**body)
    for field in ("brand", "model"):
        assert client.post("/api/cars", headers=headers, json=body | {field: "  "}).status_code == 422
        assert client.patch(car, headers=headers, json={field: "\t"}).status_code == 422
    for group, field, payload in [
        ("notes", "text", {"kind": "report", "text": "  Осмотр  "}),
        ("benefits", "title", {"title": "Резина", "amount": 0}),
        ("expenses", "title", {"title": "Осмотр", "kind": "other", "amount": 0}),
    ]:
        path = f"{car}/{group}"
        response = client.post(path, headers=headers, json=payload | {field: " \t\n"})
        assert response.status_code == 422
        saved = client.post(path, headers=headers, json=payload).json()
        assert saved[field] == payload[field]
        response = client.patch(f"{path}/{saved['id']}", headers=headers, json={field: "\u00a0"})
        assert response.status_code == 422
        assert client.get(car, headers=headers).json()[group] == [saved]


@pytest.mark.parametrize("field,bad,good", [
    ("fetched_at", "2025-01-02T12:34Z", "2024-02-29T12:34:00.123456789Z"),
    ("fetched_at", "2025-01-02T12:34:56+0300", "2025-01-02t12:34:56+03:00"),
    ("fetched_at", "2025-02-29T00:00:00Z", "2016-12-31T23:59:60Z"),
    ("fetched_at", "2025-02-28T00:00:00", "2025-02-28T00:00:00-00:00"),
    ("listing_url", "https://example.invalid/a b", "https://example.invalid/a%20b"),
    ("image_url", "relative/path", "urn:example:photo"),
    ("image_url", "https://example.invalid/%xx", "https://[::1]/a?x=y#f"),
])
def test_wire_formats(client, headers, field, bad, good):
    body = {"source": "manual", "brand": "A", "model": "B", "price": 1}
    rejected = client.post("/api/cars", headers=headers, json=body | {field: bad})
    assert rejected.status_code == 422
    saved = client.post("/api/cars", headers=headers, json=body | {field: good})
    assert saved.status_code == 201
    path = "/api/cars/" + saved.json()["id"]
    if field != "fetched_at":
        assert client.patch(path, headers=headers, json={field: bad}).status_code == 422
    assert client.get(path, headers=headers).json()[field] == good


def test_invalid_import_uri_is_rejected_before_provider(db_path):
    async def unexpected(_url):
        pytest.fail("Provider received an invalid URI")

    with TestClient(create_app(db_path, importer=unexpected)) as client:
        token = client.post("/api/session").json()["token"]
        response = client.post("/api/cars/import", headers={"Authorization": "Bearer " + token},
                               json={"url": "https://example.invalid/%xx"})
        assert response.status_code == 400
        assert response.json()["code"] == "invalid_url"


def test_cors_on_unhandled_errors(db_path, monkeypatch):
    allowed = "http://localhost:5173"
    monkeypatch.setenv("DROM_CORS_ORIGINS", allowed)

    async def failing_provider(_url):
        raise RuntimeError("provider failed")

    with TestClient(create_app(db_path, importer=failing_provider), raise_server_exceptions=False) as client:
        token = client.post("/api/session").json()["token"]
        for origin in (allowed, "https://other.invalid"):
            response = client.post("/api/cars/import", json={"url": "urn:test:car"},
                                   headers={"Authorization": "Bearer " + token, "Origin": origin})
            assert response.status_code == 500
            assert response.json()["code"] == "internal_error"
            assert "Origin" in response.headers["vary"]
            assert response.headers.get("access-control-allow-origin") == (allowed if origin == allowed else None)


def test_previously_accepted_card_can_be_read_and_corrected(client, headers, new_car, db_path):
    path = new_car()
    with sqlite3.connect(db_path) as connection:
        connection.execute(
            "UPDATE cars SET brand = ?, listing_url = ? WHERE id = ?",
            (" ", "https://example.invalid/%xx", path.rsplit("/", 1)[1]),
        )
    saved = client.get(path, headers=headers)
    assert saved.status_code == 200 and saved.json()["brand"] == " "
    assert saved.json()["listing_url"] is None
    assert client.get("/api/cars", headers=headers).status_code == 200
    corrected = client.patch(path, headers=headers, json={"brand": "Марка", "listing_url": None})
    assert corrected.status_code == 200 and corrected.json()["listing_url"] is None
