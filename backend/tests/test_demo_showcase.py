import asyncio
import json

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import text

from app.main import create_app
from app.services.listings import DEFAULT_FIXTURE_PATH, ListingImportError
from app.services.listings.importer import importer_from_env


FIXTURES = DEFAULT_FIXTURE_PATH.parent
SHOWCASE_PATH = FIXTURES / "demo-showcase.json"
SHOWCASE = json.loads(SHOWCASE_PATH.read_text("utf-8"))
STANDARD = json.loads(DEFAULT_FIXTURE_PATH.read_text("utf-8"))
ASSESSMENTS = json.loads((FIXTURES / "demo-showcase-assessments.json").read_text("utf-8"))
CHECKS = [
    ("Toyota", "Camry", 2015, 1_200_000, 145_000, 29_000, 45_000, 1_229_000, 1_184_000, "complete", 0),
    ("Honda", "Accord", 2016, 1_350_000, 130_000, 130_000, 25_000, 1_480_000, 1_455_000, "complete", 0),
    ("Mazda", "6", 2017, 1_450_000, 112_000, 15_000, 12_000, 1_465_000, 1_453_000, "incomplete", 1),
    ("Nissan", "X-Trail", 2015, 1_550_000, 160_000, 0, 55_000, 1_550_000, 1_495_000, "complete", 0),
    ("Toyota", "RAV4", 2016, 1_850_000, 98_000, 0, 0, 1_850_000, 1_850_000, "incomplete", 0),
]


@pytest.fixture
def showcase_enabled(monkeypatch):
    monkeypatch.setenv("DROM_LISTINGS_EXTRA_FIXTURE", str(SHOWCASE_PATH))


def test_showcase_is_opt_in():
    importer = importer_from_env({})
    with pytest.raises(ListingImportError) as error:
        asyncio.run(importer(SHOWCASE[0]["listing_url"]))
    assert error.value.code == "listing_unavailable"


@pytest.mark.parametrize("listing", SHOWCASE, ids=lambda row: row["listing_id"])
def test_network_provider_rejects_showcase_before_fetch(listing, monkeypatch):
    from app.services.listings import drom_provider

    def unexpected_network(*args):
        pytest.fail("Synthetic URL reached the network transport")

    monkeypatch.setattr(drom_provider, "get_html", unexpected_network)
    importer = importer_from_env({
        "DROM_LISTINGS_SOURCE": "drom",
        "DROM_LISTINGS_ACCESS_APPROVED": "yes",
        "DROM_LISTINGS_HOSTS": "auto.drom.ru",
        "DROM_LISTINGS_EXTRA_FIXTURE": str(SHOWCASE_PATH),
    })
    with pytest.raises(ListingImportError) as error:
        asyncio.run(importer(listing["listing_url"]))
    assert error.value.code == "listing_unavailable"


@pytest.mark.parametrize("listing", STANDARD, ids=lambda row: row["listing_id"])
def test_showcase_keeps_standard_previews(showcase_enabled, client, headers, listing):
    response = client.post("/api/cars/import", headers=headers, json={"url": listing["listing_url"]})
    assert response.status_code == 200
    assert response.json() == listing


def test_showcase_keeps_missing_listing(showcase_enabled, client, headers):
    response = client.post(
        "/api/cars/import", headers=headers, json={"url": "https://auto.drom.ru/demo/404.html"},
    )
    assert response.status_code == 404
    assert response.json()["code"] == "listing_unavailable"


def test_showcase_api_persistence_and_comparison(showcase_enabled, client, headers, new_session, sql_engine, db_url):
    def request(method, path, status=200, body=None, auth=headers):
        response = client.request(method, path, headers=auth, json=body)
        assert response.status_code == status, response.text
        return response.json()

    ids = []
    summaries = []
    for number, (listing, check) in enumerate(zip(SHOWCASE, CHECKS, strict=True), start=101):
        brand, model, year, price, mileage, expenses, benefits, cost, adjusted, status, unknown = check
        assert (listing["brand"], listing["model"], listing["year"], listing["price"], listing["mileage_km"]) == (
            brand, model, year, price, mileage,
        )
        assert listing["listing_url"] == f"https://auto.drom.ru/demo/{number}.html"
        assert listing["market_valuation"] is None and listing["fetched_at"] is None
        assert all(listing[field] for field in (
            "trim", "fuel_type", "engine_volume_l", "power_hp", "transmission", "drive_type", "city",
        ))
        paths = listing["image_urls"]
        assert len(set(paths)) == 3 and all(path.startswith("/demo/vehicles/") for path in paths)
        assert listing["image_url"] == paths[0]

        preview = request("POST", "/api/cars/import", body={"url": listing["listing_url"]})
        assert preview == listing
        assert len(request("GET", "/api/cars")) == len(ids)
        car = request("POST", "/api/cars", 201, preview)
        assert "image_urls" not in car
        car_id = car["id"]
        ids.append(car_id)
        path = f"/api/cars/{car_id}"
        scenario = ASSESSMENTS[listing["listing_id"]]
        for collection in ("expenses", "benefits", "notes"):
            for entry in scenario[collection]:
                request("POST", f"{path}/{collection}", 201, entry)
        flags = {field: scenario[field] for field in ("expenses_reviewed", "benefits_reviewed")}
        if any(flags.values()):
            request("PATCH", path, body=flags)

        detail = request("GET", path)
        assert all(detail[field] == value for field, value in preview.items())
        assert all(detail[field] == value for field, value in flags.items())
        for collection in ("expenses", "benefits", "notes"):
            assert len(detail[collection]) == len(scenario[collection])
            for entry in scenario[collection]:
                assert any(all(item[key] == value for key, value in entry.items())
                           for item in detail[collection])
        summary = request("GET", path + "/summary")
        assert summary == {
            "car_id": car_id, "listing_price": price, "total_expenses": expenses,
            "total_benefits": benefits, "acquisition_cost": cost, "adjusted_cost": adjusted,
            "assessment_status": status, "unknown_expenses": unknown, "unknown_benefits": 0,
        }
        summaries.append(summary)

        with sql_engine.connect() as connection:
            stored = connection.execute(text(
                "SELECT image_urls, expenses_reviewed, benefits_reviewed, market_valuation "
                "FROM cars WHERE id = :id"
            ), {"id": car_id}).mappings().one()
            assert dict(stored) == {"image_urls": paths, "market_valuation": None, **flags}
            assert connection.scalar(text(
                "SELECT count(*) FROM expenses WHERE car_id = :id AND amount IS NULL"
            ), {"id": car_id}) == unknown

    for count in (2, 5):
        comparison = request("POST", "/api/comparison", body={"car_ids": ids[:count]})
        assert comparison["provisional"] == (count == 5)
        assert [warning["code"] for warning in comparison["warnings"]] == (
            ["incomplete"] if count == 5 else []
        )
        assert comparison["items"] == [
            summary | {"difference_from_min": summary["adjusted_cost"] - 1_184_000}
            for summary in summaries[:count]
        ]

    guest = new_session()
    assert request("GET", "/api/cars", auth=guest) == []
    assert request("GET", f"/api/cars/{ids[0]}", 404, auth=guest)["code"] == "not_found"
    assert request("POST", "/api/comparison", 404, {"car_ids": ids}, guest)["code"] == "not_found"

    with TestClient(create_app(db_url)) as restarted:
        response = restarted.post("/api/comparison", headers=headers, json={"car_ids": ids})
        assert response.status_code == 200
        assert response.json() == comparison
        for car_id, listing in zip(ids, SHOWCASE, strict=True):
            detail = restarted.get(f"/api/cars/{car_id}", headers=headers)
            assert detail.status_code == 200
            assert detail.json()["image_urls"] == listing["image_urls"]
