import json
from decimal import Decimal

import pytest

from app.money import MONEY_LIMIT
from app.services.listings.drom_provider import listing_from_html
from app.services.listings.errors import ListingImportError
from app.services.listings.normalize import normalize_listing, parse_price
from app.services.listings.urls import HostAllowlist, validate_listing_url


TARGET = validate_listing_url(
    "https://auto.drom.ru/demo/test/12345678.html", HostAllowlist(("auto.drom.ru",)),
)


def source_page(offer):
    vehicle = {
        "@type": "Car", "sku": TARGET.listing_id, "brand": "Demo", "model": "Test", "offers": offer,
    }
    return '<script type="application/ld+json">' + json.dumps(vehicle) + '</script>'


@pytest.mark.parametrize("raw", [MONEY_LIMIT, str(MONEY_LIMIT), "1 000 000 000 000", "1 000 000 000 000 ₽"])
def test_listing_price_exact_at_the_cap(raw):
    listing = listing_from_html(source_page({"price": raw, "priceCurrency": "RUB"}), TARGET)
    assert listing.price == MONEY_LIMIT


@pytest.mark.parametrize("offer", [
    {"price": MONEY_LIMIT + 1, "priceCurrency": "RUB"},
    {"price": 1.5, "priceCurrency": "RUB"},
    {"price": 0, "priceCurrency": "RUB"},
    {"price": -1, "priceCurrency": "RUB"},
    {"price": "1 0", "priceCurrency": "RUB"},
    {"price": "1 zł", "priceCurrency": "RUB"},
    {"price": 42, "priceCurrency": "USD"},
    {"price": 42, "priceCurrency": "RUB", "priceSpecification": {"price": 43}},
])
def test_invalid_price_never_becomes_missing(offer):
    with pytest.raises(ListingImportError, match="parse_error"):
        listing_from_html(source_page(offer), TARGET)


def test_canonical_page_can_confirm_a_visible_price_when_offer_lacks_it():
    html = (f'<link rel="canonical" href="{TARGET.canonical}">'
            '<div data-ftid="bulletin-price">1 000 000 ₽</div>'
            + source_page({"priceCurrency": "RUB"}))
    assert listing_from_html(html, TARGET).price == 1_000_000


@pytest.mark.parametrize("visible", ["1 000 001 ₽", "1000 USD", "договорная"])
def test_conflicting_visible_price_is_not_treated_as_absent(visible):
    html = (f'<link rel="canonical" href="{TARGET.canonical}">'
            f'<div data-ftid="bulletin-price">{visible}</div>'
            + source_page({"price": 1000000, "priceCurrency": "RUB"}))
    with pytest.raises(ListingImportError, match="parse_error"):
        listing_from_html(html, TARGET)


def test_json_ld_does_not_round_a_near_integer_to_an_acceptable_price():
    assert parse_price(Decimal("1.0000000000000001")) is None
    html = source_page({"price": "TOKEN", "priceCurrency": "RUB"})
    with pytest.raises(ListingImportError, match="parse_error"):
        listing_from_html(html.replace('"TOKEN"', "1.0000000000000001"), TARGET)
    result = listing_from_html(html.replace('"TOKEN"', "1e12"), TARGET)
    assert result.price == MONEY_LIMIT


@pytest.mark.parametrize("raw", [0, False, "unknown", MONEY_LIMIT + 1, Decimal("1.0000000000000001")])
def test_invalid_optional_valuation_is_ignored_and_diagnostics_are_safe(raw, caplog):
    with caplog.at_level("INFO"):
        result = normalize_listing(
            {"price": 7, "market_valuation": raw}, source="drom",
            listing_url=TARGET.canonical, listing_id=TARGET.listing_id, fetched_at=None,
        )
    assert result.price == 7
    assert result.market_valuation is None
    assert caplog.messages == ["listing market valuation ignored: invalid amount"]
