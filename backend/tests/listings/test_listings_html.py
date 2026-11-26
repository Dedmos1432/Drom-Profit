import asyncio
import json
from pathlib import Path

import pytest

from app.services.listings.drom_provider import DromHtmlProvider, listing_from_html
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
        "image_urls": ["https://img.example.test/main.jpg", "https://img.example.test/2.jpg"],
        "listing_url": URL,
        "market_valuation": None,
        "fetched_at": None,
    }
    assert data["fetched_at"].endswith("+00:00")


def test_drom_provider_rejects_an_empty_listing():
    empty = page({"@type": "Car", "sku": TARGET.listing_id}).encode()
    provider = DromHtmlProvider(access_approved=True, allowed_hosts=HOSTS, client=FakeClient(empty))
    with pytest.raises(ListingImportError) as error:
        asyncio.run(ListingImporter(provider)(URL))
    assert error.value.code == "parse_error"


@pytest.mark.parametrize(
    "offer",
    [
        {"price": 500000},
        {"price": 500000, "priceCurrency": "USD"},
        {"price": 0, "priceCurrency": "RUB"},
    ],
    ids=["no-currency", "foreign-currency", "zero-price"],
)
def test_drom_provider_rejects_an_invalid_price_even_with_brand_and_model(offer):
    vehicle = {"@type": "Car", "sku": TARGET.listing_id, "brand": "Demo", "model": "Example"}
    if offer:
        vehicle["offers"] = offer
    provider = DromHtmlProvider(
        access_approved=True, allowed_hosts=HOSTS, client=FakeClient(page(vehicle).encode()),
    )
    with pytest.raises(ListingImportError) as error:
        asyncio.run(ListingImporter(provider)(URL))
    assert error.value.code == "parse_error"


@pytest.mark.parametrize("offer", [None, {}, {"priceCurrency": "RUB"}, {"price": None}])
def test_recognized_listing_can_be_imported_without_a_price(offer):
    vehicle = {"@type": "Car", "sku": TARGET.listing_id, "brand": "Demo", "model": "Example"}
    if offer is not None:
        vehicle["offers"] = offer
    listing = listing_from_html(page(vehicle), TARGET)
    assert (listing.brand, listing.model, listing.price) == ("Demo", "Example", None)


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
        "offers": {"price": 700000, "priceCurrency": "RUB"},
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
        "@type": "Car", "url": URL, "brand": "Demo",
        "offers": {"price": 700000, "priceCurrency": "RUB"},
        "image": [
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
    assert len(public) == 19
    assert public["image_urls"] == [
        "https://vladivostok.drom.ru/gallery/main.jpg", "https://img.example.test/second.jpg",
    ]


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
    assert listing_from_html(
        metas + page({"@type": "Car", "sku": TARGET.listing_id, "image": ["data:invalid"],
                      "brand": "Demo", "model": "Model"}), TARGET,
    ).as_dict()["image_urls"] == ([expected] if expected else [])



def test_matches_explicit_offer_and_reads_only_the_bulletin_markup():
    html = f"""
    <link rel="canonical" href="{URL}">
    <meta property="og:url" content="{URL}">
    <table data-ftid="bulletin-specifications">
      <tr data-ftid="specification-engine"><th>Двигатель</th><td data-ftid="value">дизель, 2,4 л</td></tr>
      <tr data-ftid="specification-power"><td data-ftid="value">
        <style>.text {{ color: red }}</style><span>125 л.с.</span>,&nbsp;<button>налог</button>
      </td></tr>
      <tr data-ftid="specification-complectation"><td data-ftid="value">Комплектация A</td></tr>
      <tr data-ftid="specification-drive"><td data-ftid="value">4WD</td></tr>
      <tr data-ftid="specification-transmission"><td data-ftid="value">АКПП</td></tr>
    </table>
    <div data-ftid="bulletin-description">
      <div data-ftid="city"><span data-ftid="property">Город:</span><span data-ftid="value">Тестовый город</span></div>
    </div>
    <div data-ftid="city"><span data-ftid="value">Вне объявления</span></div>
    <div data-ftid="bull-page_bull-gallery_thumbnails">
      <a data-ga-stats-name="gallery-thumbnail" href="/gallery/one.jpg"></a>
      <a data-ga-stats-name="gallery-thumbnail" href="/gallery/two.jpg"></a>
      <a data-ga-stats-name="gallery-thumbnail" href="/gallery/one.jpg#preview"></a>
    </div>
    <a data-ga-stats-name="gallery-thumbnail" href="/gallery/recommended.jpg"></a>
    """ + page({
        "@graph": [
            {"@type": "Car", "brand": "Other", "offers": {"url": "/other/98765432.html"}},
            {
                "@type": "Car", "brand": "Sample", "model": "Example",
                "image": {"url": "/gallery/social.jpg"},
                "offers": {"@type": "Offer", "url": URL, "price": 700000, "priceCurrency": "RUB"},
            },
        ],
    })
    raw = extract_listing_fields(html, target=TARGET)
    listing = listing_from_html(html, TARGET)
    assert (listing.listing_id, listing.brand, listing.price) == (TARGET.listing_id, "Sample", 700000)
    assert (listing.fuel_type, listing.engine_volume_l, listing.power_hp) == ("Дизель", 2.4, 125)
    assert (listing.trim, listing.drive_type, listing.transmission, listing.city) == (
        "Комплектация A", "Полный", "Автомат", "Тестовый город",
    )
    assert raw["image_urls"] == (
        "https://vladivostok.drom.ru/gallery/one.jpg",
        "https://vladivostok.drom.ru/gallery/two.jpg",
    )
    assert listing.as_dict()["image_urls"] == list(raw["image_urls"])


def test_matching_offer_cannot_overrule_an_explicitly_mismatched_vehicle():
    html = page({
        "@type": "Car", "sku": "98765432", "brand": "Wrong",
        "offers": {"url": URL, "price": 700000, "priceCurrency": "RUB"},
    })
    with pytest.raises(ListingImportError, match="parse_error"):
        extract_listing_fields(html, target=TARGET)


def test_conflicting_page_identity_cannot_supply_fallback_fields():
    html = '<link rel="canonical" href="/other/98765432.html">' + page({
        "@type": "Car", "sku": TARGET.listing_id,
        "offers": {"url": URL, "price": 700000, "priceCurrency": "RUB"},
    })
    with pytest.raises(ListingImportError, match="parse_error"):
        extract_listing_fields(html, target=TARGET)


def test_unverified_page_markup_does_not_override_structured_values():
    table = """
    <table data-ftid="bulletin-specifications">
      <tr data-ftid="specification-drive"><td data-ftid="value">4WD</td></tr>
      <tr data-ftid="specification-power"><td data-ftid="value">180 л.с.</td></tr>
    </table>
    """
    car = page({
        "@type": "Car", "sku": TARGET.listing_id,
        "offers": {"price": 700000, "priceCurrency": "RUB"},
        "driveWheelConfiguration": "FrontWheelDriveConfiguration",
        "vehicleEngine": {"enginePower": {"value": 180, "unitCode": "unknown"}},
    })
    listing = listing_from_html(f'<link rel="canonical" href="{URL}">' + table + car, TARGET)
    assert listing.drive_type == "Передний"
    assert listing.power_hp is None
    assert listing_from_html(table + car, TARGET).power_hp is None
