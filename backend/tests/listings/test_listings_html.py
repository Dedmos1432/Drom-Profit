import asyncio
import json
from pathlib import Path

import pytest

from app.services.listings.drom_provider import DromHtmlProvider
from app.services.listings.errors import ListingImportError
from app.services.listings.html_parser import _image_urls, extract_listing_fields
from app.services.listings.importer import ListingImporter
from app.services.listings.safe_http import HttpResponse
from app.services.listings.urls import HostAllowlist, validate_listing_url

SAMPLE = (Path(__file__).parent / "samples" / "synthetic_listing.html").read_text("utf-8")
URL = "https://vladivostok.drom.ru/toyota/camry/12345678.html"
HOSTS = HostAllowlist(("vladivostok.drom.ru",))
TARGET = validate_listing_url(URL, HOSTS)


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
    provider = DromHtmlProvider(
        access_approved=True, allowed_hosts=HOSTS, client=FakeClient(SAMPLE.encode())
    )
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
    empty = page({"@type": "Car", "sku": TARGET.listing_id}).encode()
    provider = DromHtmlProvider(access_approved=True, allowed_hosts=HOSTS, client=FakeClient(empty))
    with pytest.raises(ListingImportError) as error:
        asyncio.run(ListingImporter(provider)(URL))
    assert error.value.code == "parse_error"


def test_drom_provider_is_disabled_without_approval():
    with pytest.raises(RuntimeError):
        DromHtmlProvider(access_approved=False)


@pytest.mark.parametrize(
    ("mileage", "volume", "power", "expected"),
    [
        ({"value": 145000, "unitCode": "KMT"}, {"value": 2.5, "unitText": "л"},
         {"value": 181, "unitCode": "BHP"}, (145000, 2.5, 181)),
        ({"value": "145,5", "unitText": "тыс. км"}, {"value": 2494, "unitCode": "CMQ"},
         {"value": 133, "unitCode": "KWT", "unitText": "кВт"}, (145500, 2.5, 181)),
        ("145 тыс. км", "2494 см³", "133 кВт", (145000, 2.5, 181)),
        ({"value": 145, "unitCode": "SMI"}, {"value": 2.5, "unitText": "gallons"},
         {"value": 133, "unitCode": "unknown"}, (None, None, None)),
        ({"value": 145, "unitCode": "unknown"}, {"value": 2.5, "unitCode": "unknown"},
         {"value": 133, "unitCode": "KWT", "unitText": "HP"}, (None, None, None)),
        (145000, {"value": 2.5}, "133", (None, None, None)),
    ],
)
def test_quantities_require_known_units(mileage, volume, power, expected):
    html = page({
        "@type": "Car", "sku": TARGET.listing_id, "brand": "Demo",
        "mileageFromOdometer": mileage,
        "vehicleEngine": {"engineDisplacement": volume, "enginePower": power},
    })
    provider = DromHtmlProvider(
        access_approved=True, allowed_hosts=HOSTS, client=FakeClient(html.encode()),
    )
    listing = asyncio.run(provider.fetch(URL))
    assert (listing.mileage_km, listing.engine_volume_l, listing.power_hp) == expected


@pytest.mark.parametrize(
    ("currency", "expected"),
    [("RUB", 700000), (" rur ", 700000), ("USD", None), ("unknown", None), ("", None), (None, None)],
)
def test_preview_price_requires_declared_rubles(currency, expected):
    offer = {"price": 700000}
    if currency is not None:
        offer["priceCurrency"] = currency
    raw = extract_listing_fields(page({"@type": "Car", "offers": offer}))
    assert raw["price"] == expected
    assert raw["market_valuation"] is None


def test_selects_vehicle_and_offer_by_listing_identity():
    drawing = {
        "@graph": [
            {"@type": "Car", "sku": "87654321", "brand": "Other"},
            {
                "@type": "Vehicle", "url": TARGET.path + "?from=share", "brand": "Requested",
                "offers": {"@type": "AggregateOffer", "offers": [
                    {"url": "/toyota/camry/87654321.html", "priceCurrency": "RUB", "price": 500},
                    {"itemOffered": {"sku": TARGET.listing_id}, "priceCurrency": "RUB", "price": 700},
                ]},
            },
        ],
    }
    raw = extract_listing_fields(page(drawing), target=TARGET)
    assert (raw["brand"], raw["price"]) == ("Requested", 700)


@pytest.mark.parametrize("vehicle", [
    {"@type": "Car", "brand": "Unidentified"},
    {"@type": "Car", "brand": "Wrong", "sku": "87654321"},
    {"@type": "Car", "brand": "Conflicting", "sku": TARGET.listing_id,
     "url": "/toyota/camry/87654321.html"},
    {"@graph": [
        {"@type": "Car", "brand": "One", "sku": TARGET.listing_id},
        {"@type": "Car", "brand": "Two", "url": URL},
    ]},
    {"@type": "Car", "brand": "Demo", "sku": TARGET.listing_id, "offers": [
        {"price": 500}, {"price": 700},
    ]},
    {"@type": "Car", "brand": "Demo", "sku": TARGET.listing_id, "offers": {
        "itemOffered": {"sku": "87654321"}, "price": 500,
    }},
])
def test_refuses_ambiguous_or_mismatched_listing_data(vehicle):
    with pytest.raises(ListingImportError) as error:
        extract_listing_fields(page(vehicle), target=TARGET)
    assert error.value.code == "parse_error"


def test_public_result_skips_invalid_first_image_and_uses_relative_candidate():
    html = '<meta property="og:image" content="https://img.example.test/fallback.jpg">' + page({
        "@type": "Car", "url": URL, "brand": "Demo", "image": [
            "http://img.example.test/rejected.jpg",
            {"url": "https://", "contentUrl": "/gallery/main.jpg"},
            "https://img.example.test/second.jpg",
        ],
    })
    provider = DromHtmlProvider(
        access_approved=True, allowed_hosts=HOSTS, client=FakeClient(html.encode()),
    )
    public = asyncio.run(provider.fetch(URL)).as_dict()
    assert public["image_url"] == "https://vladivostok.drom.ru/gallery/main.jpg"
    assert len(public) == 18
    assert "image_urls" not in public


def test_image_candidates_are_unique_in_source_order():
    photos = _image_urls(
        ["/gallery/a.jpg#thumbnail", "https://vladivostok.drom.ru/gallery/a.jpg",
         {"url": "https://img.example.test/b.jpg", "contentUrl": "https://img.example.test/c.jpg"},
         "//unconfirmed.example.test/relative.jpg", "https://img.example.test/d.jpg?version=1"],
        ["/gallery/fallback.jpg"],
        URL,
    )
    assert photos == (
        "https://vladivostok.drom.ru/gallery/a.jpg",
        "https://img.example.test/b.jpg",
        "https://img.example.test/c.jpg",
        "https://img.example.test/d.jpg?version=1",
    )


@pytest.mark.parametrize("metas, expected", [
    ('<meta property="og:image" content="http://img.example.test/bad.jpg">'
     '<meta property="og:image" content="/photo.jpg">', "https://vladivostok.drom.ru/photo.jpg"),
    ("", None),
])
def test_og_image_is_a_fallback_and_absence_is_allowed(metas, expected):
    raw = extract_listing_fields(
        metas + page({"@type": "Car", "sku": TARGET.listing_id, "image": ["data:invalid"]}),
        target=TARGET,
    )
    assert raw["image_url"] == expected
