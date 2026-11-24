"""Извлечение данных объявления из HTML через структурированные данные schema.org.

Парсер не делает сетевых запросов и работает только с переданной строкой.
Используются JSON-LD блоки (``Car``/``Vehicle``/``Product`` + ``Offer``) и
``og:image``. Соответствие реальной разметке Дрома НЕ проверено: до согласования
доступа он покрыт тестами на синтетических страницах. После получения
разрешенных образцов страниц соответствие полей нужно сверить и дополнить
(в частности, рыночная оценка Дрома в schema.org не передается и пока ``None``).
"""

import json
from html.parser import HTMLParser
from typing import Any

from .errors import ListingImportError

_VEHICLE_TYPES = {"car", "vehicle", "motorizedbicycle", "product", "individualproduct"}
_UNAVAILABLE = ("soldout", "discontinued", "outofstock")
_BLOCK_MARKERS = (
    "captcha", "are you a robot", "вы робот", "подтвердите, что вы не робот",
    "доступ ограничен", "access denied", "too many requests",
)
_DRIVE_URLS = {
    "frontwheeldriveconfiguration": "Передний",
    "rearwheeldriveconfiguration": "Задний",
    "allwheeldriveconfiguration": "Полный",
    "fourwheeldriveconfiguration": "Полный",
}
MAX_JSON_LD_BLOCKS = 20
MAX_JSON_LD_LENGTH = 500_000


class _Collector(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.json_ld: list[str] = []
        self.meta: dict[str, str] = {}
        self._in_json_ld = False
        self._buffer: list[str] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        values = {key.lower(): (value or "") for key, value in attrs}
        if tag == "script" and values.get("type", "").strip().lower() == "application/ld+json":
            self._in_json_ld, self._buffer = True, []
        elif tag == "meta":
            key = (values.get("property") or values.get("name") or "").lower()
            if key and key not in self.meta and "content" in values:
                self.meta[key] = values["content"]

    def handle_endtag(self, tag: str) -> None:
        if tag == "script" and self._in_json_ld:
            self._in_json_ld = False
            if len(self.json_ld) < MAX_JSON_LD_BLOCKS:
                self.json_ld.append("".join(self._buffer)[:MAX_JSON_LD_LENGTH + 1])

    def handle_data(self, data: str) -> None:
        if self._in_json_ld:
            self._buffer.append(data)


def _walk(node: Any) -> list[dict[str, Any]]:
    """Плоский список объектов JSON-LD с учетом ``@graph`` и массивов."""
    found: list[dict[str, Any]] = []
    stack = [node]
    while stack and len(found) < 200:
        item = stack.pop()
        if isinstance(item, list):
            stack.extend(item)
        elif isinstance(item, dict):
            found.append(item)
            if "@graph" in item:
                stack.append(item["@graph"])
    return found


def _types(item: dict[str, Any]) -> set[str]:
    value = item.get("@type")
    values = value if isinstance(value, list) else [value]
    return {v.rsplit("/", 1)[-1].lower() for v in values if isinstance(v, str)}


def _first(value: Any) -> Any:
    return value[0] if isinstance(value, list) and value else value


def _name(value: Any) -> Any:
    value = _first(value)
    return value.get("name") if isinstance(value, dict) else value


def _quantity(value: Any) -> tuple[Any, str]:
    """QuantitativeValue → (значение, unitCode/unitText в нижнем регистре)."""
    value = _first(value)
    if isinstance(value, dict):
        unit = value.get("unitCode") or value.get("unitText") or ""
        return value.get("value"), str(unit).lower()
    return value, ""


def _with_unit(value: Any, unit: str, units: dict[str, str]) -> Any:
    """Добавляет к числу единицу из unitCode, чтобы нормализатор понял масштаб."""
    suffix = units.get(unit)
    if suffix and isinstance(value, (int, float, str)) and not isinstance(value, bool):
        return f"{value} {suffix}"
    return value


def _offer(item: dict[str, Any]) -> dict[str, Any]:
    offers = _first(item.get("offers"))
    if isinstance(offers, dict) and "aggregateoffer" in _types(offers):
        offers = _first(offers.get("offers")) or offers
    return offers if isinstance(offers, dict) else {}


def _city(item: dict[str, Any], offer: dict[str, Any]) -> Any:
    for holder in (offer.get("availableAtOrFrom"), offer.get("areaServed"), item.get("seller")):
        holder = _first(holder)
        if isinstance(holder, dict):
            address = _first(holder.get("address"))
            if isinstance(address, dict) and address.get("addressLocality"):
                return address["addressLocality"]
            if "city" in _types(holder) or "place" in _types(holder):
                return holder.get("name")
        elif isinstance(holder, str):
            return holder
    return None


def _drive(value: Any) -> Any:
    value = _first(value)
    if isinstance(value, str):
        key = value.rsplit("/", 1)[-1].lower()
        return _DRIVE_URLS.get(key, value)
    return value


def _transmission(value: Any) -> Any:
    value = _name(value)
    if isinstance(value, str):
        lowered = value.rsplit("/", 1)[-1].lower()
        if lowered.startswith("automatic"):
            return "Автомат"
        if lowered.startswith("manual"):
            return "Механика"
    return value


def looks_blocked(html: str) -> bool:
    sample = html[:200_000].lower()
    return any(marker in sample for marker in _BLOCK_MARKERS)


def extract_listing_fields(html: str) -> dict[str, Any]:
    """Возвращает «сырые» поля для ``normalize_listing``.

    Ошибки: ``source_forbidden`` для страниц CAPTCHA/блокировки,
    ``listing_unavailable`` для снятых с продажи, ``parse_error``, если
    структурированных данных автомобиля нет.
    """
    collector = _Collector()
    try:
        collector.feed(html)
        collector.close()
    except Exception:
        raise ListingImportError("parse_error", "HTML не разбирается") from None

    vehicle = None
    for block in collector.json_ld:
        if len(block) > MAX_JSON_LD_LENGTH:
            continue
        try:
            data = json.loads(block)
        except ValueError:
            continue
        for item in _walk(data):
            if _types(item) & _VEHICLE_TYPES:
                vehicle = item
                if _types(item) & {"car", "vehicle"}:
                    break
        if vehicle is not None and _types(vehicle) & {"car", "vehicle"}:
            break

    if vehicle is None:
        if looks_blocked(html):
            raise ListingImportError("source_forbidden", "страница проверки или блокировки")
        raise ListingImportError("parse_error", "нет структурированных данных автомобиля")

    offer = _offer(vehicle)
    availability = str(offer.get("availability") or "").rsplit("/", 1)[-1].lower()
    if availability in _UNAVAILABLE:
        raise ListingImportError("listing_unavailable", "объявление снято с продажи")

    currency = str(offer.get("priceCurrency") or "RUB").upper()
    engine = _first(vehicle.get("vehicleEngine"))
    engine = engine if isinstance(engine, dict) else {}
    volume, volume_unit = _quantity(engine.get("engineDisplacement"))
    power, power_unit = _quantity(engine.get("enginePower"))
    mileage, mileage_unit = _quantity(vehicle.get("mileageFromOdometer"))
    if mileage_unit in ("smi", "mile", "miles"):
        mileage = None  # пересчет миль не делаем: источник должен указывать км
    image = _first(vehicle.get("image"))
    if isinstance(image, dict):
        image = image.get("url") or image.get("contentUrl")

    return {
        "listing_id": vehicle.get("sku") or vehicle.get("productID"),
        "brand": _name(vehicle.get("brand")) or _name(vehicle.get("manufacturer")),
        "model": _name(vehicle.get("model")),
        "year": vehicle.get("vehicleModelDate") or vehicle.get("productionDate")
        or vehicle.get("modelDate"),
        "price": offer.get("price") if currency in ("RUB", "RUR") else None,
        "mileage_km": mileage,
        "trim": vehicle.get("vehicleConfiguration"),
        "fuel_type": _name(engine.get("fuelType")) or _name(vehicle.get("fuelType")),
        "engine_volume_l": _with_unit(volume, volume_unit, {"ltr": "л", "cmq": "см3", "l": "л"}),
        "power_hp": _with_unit(power, power_unit, {"bhp": "л.с.", "hp": "л.с.", "kwt": "кВт"}),
        "transmission": _transmission(vehicle.get("vehicleTransmission")),
        "drive_type": _drive(vehicle.get("driveWheelConfiguration")),
        "city": _city(vehicle, offer),
        "image_url": image or collector.meta.get("og:image"),
        "market_valuation": None,
    }
