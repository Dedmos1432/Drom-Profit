from datetime import date

import pytest

from app.services.listings import ListingData
from app.services.listings import normalize as n


@pytest.mark.parametrize(
    ("raw", "expected"),
    [
        (1200000, 1200000),
        ("1 200 000 ₽", 1200000),
        ("1 200 000 руб.", 1200000),
        ("1200000р.", 1200000),
        (1200000.0, 1200000),
        (0, None),
        (-5, None),
        ("от 1 200 000", None),
        ("договорная", None),
        (True, None),
        (None, None),
        ("1 200 000 $", None),
    ],
)
def test_parse_price(raw, expected):
    assert n.parse_price(raw) == expected


@pytest.mark.parametrize(
    ("raw", "expected"),
    [
        (145000, 145000),
        ("145 000 км", 145000),
        ("145 тыс. км", 145000),
        ("1,5 тыс км", 1500),
        (0, 0),
        ("без пробега по РФ", None),
        (-1, None),
        ("12.5 км", None),
    ],
)
def test_parse_mileage(raw, expected):
    assert n.parse_mileage(raw) == expected


@pytest.mark.parametrize(
    ("raw", "expected"),
    [
        (2.5, 2.5),
        ("2,5 л", 2.5),
        ("2.0 л.", 2.0),
        ("2494 см³", 2.5),
        ("1598 см3", 1.6),
        (1998, 2.0),
        (0, None),
        ("много", None),
        (float("nan"), None),
    ],
)
def test_parse_engine_volume(raw, expected):
    assert n.parse_engine_volume(raw) == expected


@pytest.mark.parametrize(
    ("raw", "expected"),
    [(181, 181), ("181 л.с.", 181), ("150 hp", 150), ("133 кВт", 181), (0, None), ("?", None)],
)
def test_parse_power(raw, expected):
    assert n.parse_power(raw) == expected


def test_parse_year_bounds():
    today = date(2026, 11, 24)
    assert n.parse_year("2015 г.", today) == 2015
    assert n.parse_year(2027, today) == 2027
    assert n.parse_year(2028, today) is None
    assert n.parse_year(1800, today) is None


@pytest.mark.parametrize(
    ("func", "raw", "expected"),
    [
        (n.normalize_fuel, "бензин", "Бензин"),
        (n.normalize_fuel, " ДИЗЕЛЬ ", "Дизель"),
        (n.normalize_fuel, "ГБО", "Газ"),
        (n.normalize_transmission, "АКПП", "Автомат"),
        (n.normalize_transmission, "механика", "Механика"),
        (n.normalize_transmission, "CVT", "Вариатор"),
        (n.normalize_drive, "4WD", "Полный"),
        (n.normalize_drive, "передний", "Передний"),
        (n.normalize_drive, "Подключаемый полный", "Подключаемый полный"),
        (n.normalize_drive, "—", None),
        (n.normalize_fuel, "", None),
    ],
)
def test_vocabularies_keep_unknown_values(func, raw, expected):
    assert func(raw) == expected


def test_split_engine():
    assert n.split_engine("бензин, 2.5 л") == ("Бензин", 2.5)
    assert n.split_engine("дизель") == ("Дизель", None)
    assert n.split_engine(None) == (None, None)


@pytest.mark.parametrize(
    "raw",
    ["http://img.example/1.jpg", "https://user@img.example/1.jpg", "javascript:alert(1)",
     "https://", "https://img.example/a b.jpg", 42],
)
def test_https_url_rejects_unsafe(raw):
    assert n.https_url(raw) is None


def test_normalize_listing_fills_missing_with_null():
    listing = n.normalize_listing(
        {"brand": " Toyota ", "model": "Camry", "price": "1 200 000 ₽", "engine": "бензин, 2.5 л"},
        source="drom",
        listing_url="https://vladivostok.drom.ru/toyota/camry/12345678.html",
        listing_id="12345678",
        fetched_at="2026-11-24T10:00:00+00:00",
    )
    data = listing.as_dict()
    assert list(data) == [
        "source", "listing_id", "brand", "model", "year", "price", "mileage_km", "trim",
        "fuel_type", "engine_volume_l", "power_hp", "transmission", "drive_type", "city",
        "image_url", "listing_url", "market_valuation", "fetched_at",
    ]
    assert data["brand"] == "Toyota"
    assert data["price"] == 1200000
    assert data["fuel_type"] == "Бензин"
    assert data["engine_volume_l"] == 2.5
    assert data["listing_id"] == "12345678"
    for key in ("year", "mileage_km", "trim", "power_hp", "transmission", "drive_type", "city",
                "image_url", "market_valuation"):
        assert data[key] is None, key


@pytest.mark.parametrize(
    "overrides",
    [
        {"source": "manual"},
        {"price": 0},
        {"year": 1700},
        {"mileage_km": -1},
        {"brand": "  "},
        {"engine_volume_l": -1.0},
        {"listing_url": "http://auto.drom.ru/demo/1.html"},
        {"fetched_at": "2026-11-24T10:00:00"},
        {"power_hp": True},
    ],
)
def test_listing_data_enforces_contract(overrides):
    with pytest.raises(ValueError):
        ListingData(**({"source": "demo"} | overrides))


def test_from_dict_requires_exact_fields():
    with pytest.raises(ValueError):
        ListingData.from_dict({"source": "demo"})
