import asyncio
import json
from pathlib import Path

import pytest

from app.services.listings.drom_provider import DromHtmlProvider
from app.services.listings.errors import ListingImportError
from app.services.listings.html_parser import extract_listing_fields
from app.services.listings.importer import ListingImporter
from app.services.listings.safe_http import HttpResponse
from app.services.listings.urls import HostAllowlist, validate_listing_url

SAMPLE = (Path(__file__).parent / "samples" / "synthetic_listing.html").read_text("utf-8")
URL = "https://vladivostok.drom.ru/toyota/camry/12345678.html"


def page(vehicle: dict) -> str:
    return f'<html><script type="application/ld+json">{json.dumps(vehicle)}</script></html>'


def code_of(html):
    with pytest.raises(ListingImportError) as error:
        extract_listing_fields(html)
    return error.value.code


def test_extracts_schema_org_car():
    raw = extract_listing_fields(SAMPLE)
    assert raw["brand"] == "Toyota"
    assert raw["model"] == "Camry"
    assert raw["price"] == "1200000"
    assert raw["city"] == "Владивосток"
    assert raw["image_url"] == "https://img.example.test/main.jpg"
    assert raw["drive_type"] == "Передний"
    assert raw["market_valuation"] is None


def test_missing_fields_stay_missing():
    raw = extract_listing_fields(page({"@type": "Car", "brand": "Lada", "offers": {"price": 500000}}))
    assert raw["brand"] == "Lada"
    assert raw["mileage_km"] is None and raw["model"] is None and raw["city"] is None


def test_foreign_currency_and_miles_are_not_converted():
    raw = extract_listing_fields(page({
        "@type": "Car", "brand": "BMW",
        "offers": {"price": 10000, "priceCurrency": "USD"},
        "mileageFromOdometer": {"value": 1000, "unitCode": "SMI"},
    }))
    assert raw["price"] is None and raw["mileage_km"] is None


def test_graph_and_errors():
    graph = {"@graph": [{"@type": "WebPage"}, {"@type": "Vehicle", "brand": "Kia"}]}
    assert extract_listing_fields(page(graph))["brand"] == "Kia"
    assert code_of("<html><body>Нет данных</body></html>") == "parse_error"
    assert code_of('<script type="application/ld+json">{broken</script>') == "parse_error"
    assert code_of("<html><body>Подтвердите, что вы не робот: captcha</body></html>") == (
        "source_forbidden"
    )
    sold = {"@type": "Car", "brand": "Kia", "offers": {"availability": "https://schema.org/SoldOut"}}
    assert code_of(page(sold)) == "listing_unavailable"


class FakeClient:
    def __init__(self, body: bytes, url: str = URL):
        self.body, self.url = body, url

    def get_html(self, url):
        hosts = HostAllowlist(["*.drom.ru"])
        return HttpResponse(
            url=validate_listing_url(self.url, hosts), status=200,
            headers={"content-type": "text/html; charset=utf-8"}, body=self.body,
        )


def test_drom_provider_end_to_end_without_network():
    provider = DromHtmlProvider(access_approved=True, client=FakeClient(SAMPLE.encode()))
    listing = asyncio.run(ListingImporter(provider)(URL))
    data = listing.as_dict()
    assert data | {"fetched_at": None} == {
        "source": "drom",
        "listing_id": "12345678",
        "brand": "Toyota",
        "model": "Camry",
        "year": 2015,
        "price": 1200000,
        "mileage_km": 145000,
        "trim": "2.5 Elegance Plus",
        "fuel_type": "Бензин",
        "engine_volume_l": 2.5,
        "power_hp": 181,
        "transmission": "Автомат",
        "drive_type": "Передний",
        "city": "Владивосток",
        "image_url": "https://img.example.test/main.jpg",
        "listing_url": URL,
        "market_valuation": None,
        "fetched_at": None,
    }
    assert data["fetched_at"].endswith("+00:00")


def test_drom_provider_requires_some_core_fields():
    empty = page({"@type": "Car"}).encode()
    provider = DromHtmlProvider(access_approved=True, client=FakeClient(empty))
    with pytest.raises(ListingImportError) as error:
        asyncio.run(ListingImporter(provider)(URL))
    assert error.value.code == "parse_error"


def test_drom_provider_is_disabled_without_approval():
    with pytest.raises(RuntimeError):
        DromHtmlProvider(access_approved=False)
