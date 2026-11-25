"""Извлечение данных объявления из HTML через структурированные данные schema.org.

Парсер не делает сетевых запросов и работает только с переданной строкой.
Используются JSON-LD блоки (``Car``/``Vehicle``/``Product`` + ``Offer``) и
``og:image``. Соответствие реальной разметке Дрома НЕ проверено: до согласования
доступа он покрыт тестами на синтетических страницах. После получения
разрешенных образцов страниц соответствие полей нужно сверить и дополнить
(в частности, рыночная оценка Дрома в schema.org не передается и пока ``None``).
"""

import json
from collections import deque
from html.parser import HTMLParser
from typing import Any
from urllib.parse import urljoin, urlsplit

from .drom_markup import DromPageMarkup
from .errors import ListingImportError
from .normalize import https_url
from .urls import HostAllowlist, ListingUrl, validate_listing_url

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
        self.og_images: list[str] = []
        self.page_urls: list[str] = []
        self._in_json_ld = False
        self._buffer: list[str] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        values = {key.lower(): (value or "") for key, value in attrs}
        if tag == "script" and values.get("type", "").strip().lower() == "application/ld+json":
            self._in_json_ld, self._buffer = True, []
        elif tag == "link" and "canonical" in values.get("rel", "").lower().split():
            if values.get("href"):
                self.page_urls.append(values["href"])
        elif tag == "meta":
            key = (values.get("property") or values.get("name") or "").lower()
            if key == "og:image" and values.get("content"):
                self.og_images.append(values["content"])
            elif key == "og:url" and values.get("content"):
                self.page_urls.append(values["content"])

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


_MILEAGE_UNITS = {
    "kmt": "км", "km": "км", "км": "км", "километр": "км", "километры": "км",
    "тыс. км": "тыс. км", "тыс км": "тыс. км",
}
_VOLUME_UNITS = {
    "ltr": "л", "l": "л", "л": "л", "литр": "л", "литры": "л",
    "cmq": "см3", "cm3": "см3", "cm³": "см3", "см3": "см3", "см³": "см3", "cc": "см3",
}
_POWER_UNITS = {
    "bhp": "л.с.", "hp": "л.с.", "л.с.": "л.с.", "л. с.": "л.с.",
    "kwt": "кВт", "kw": "кВт", "квт": "кВт",
}


def _quantity(value: Any, units: dict[str, str]) -> str | None:
    value = _first(value)
    if isinstance(value, dict):
        declared = [value[key] for key in ("unitCode", "unitText") if value.get(key) is not None]
        amount = value.get("value")
        if declared:
            suffixes = {
                units.get(unit.strip().lower()) if isinstance(unit, str) else None
                for unit in declared
            }
            if None in suffixes or len(suffixes) != 1 or type(amount) not in (str, int, float):
                return None
            return f"{amount} {suffixes.pop()}"
        value = amount
    # Без единицы годится только текст с собственным обозначением, которое проверит нормализатор.
    if isinstance(value, str) and any(ch.isalpha() for ch in value):
        return value
    return None


def _identity(item: dict[str, Any], target: ListingUrl) -> bool | None:
    """True — совпадение, False — явное расхождение, None — данных для сверки нет."""
    matches: list[bool] = []
    for key in ("sku", "productID"):
        value = item.get(key)
        if target.listing_id and type(value) in (str, int):
            identifier = str(value).strip()
            if identifier.isascii() and identifier.isdecimal():
                matches.append(identifier == target.listing_id)
    for key in ("url", "@id", "mainEntityOfPage"):
        value = _first(item.get(key))
        if isinstance(value, dict):
            value = value.get("url") or value.get("@id")
        if not isinstance(value, str) or not value or value.startswith("#"):
            continue
        try:
            parts = urlsplit(value)
            if parts.scheme and parts.scheme not in ("http", "https"):
                continue
            if not parts.path and not parts.netloc:
                continue
            linked = validate_listing_url(
                urljoin(target.canonical, value), HostAllowlist((target.host,))
            )
            matches.append(linked.canonical == target.canonical)
        except (ValueError, ListingImportError):
            matches.append(False)
    return all(matches) if matches else None


def _offer_identity(offer: dict[str, Any], target: ListingUrl) -> bool | None:
    signals = [_identity(offer, target)]
    offered = offer.get("itemOffered")
    if isinstance(offered, dict):
        signals.append(_identity(offered, target))
    return False if False in signals else True if True in signals else None


def _select_vehicle(
    candidates: list[dict[str, Any]], target: ListingUrl | None
) -> tuple[dict[str, Any], dict[str, Any]]:
    matched: list[tuple[dict[str, Any], dict[str, Any] | None]] = []
    for item in candidates:
        if target is None or (identity := _identity(item, target)) is True:
            matched.append((item, None))
        elif identity is None:
            try:
                offer = _offer(item, target)
            except ListingImportError:
                continue
            if _offer_identity(offer, target) is True:
                matched.append((item, offer))
    if len(matched) != 1:
        raise ListingImportError("parse_error", "автомобиль не удалось однозначно связать с объявлением")
    vehicle, offer = matched[0]
    return vehicle, _offer(vehicle, target) if offer is None else offer


def _offer(item: dict[str, Any], target: ListingUrl | None) -> dict[str, Any]:
    values = item.get("offers")
    pending = deque(values if isinstance(values, list) else [values])
    offers: list[dict[str, Any]] = []
    examined = 0
    while pending:
        examined += 1
        if examined > 200:
            raise ListingImportError("parse_error", "в объявлении слишком много предложений")
        entry = pending.popleft()
        if not isinstance(entry, dict):
            continue
        if "aggregateoffer" in _types(entry) and entry.get("offers"):
            children = entry["offers"]
            pending.extendleft(reversed(children if isinstance(children, list) else [children]))
        else:
            offers.append(entry)
    if not offers:
        return {}

    if target is not None:
        identities = [_offer_identity(offer, target) for offer in offers]
        matched = [offer for offer, identity in zip(offers, identities) if identity is True]
        if matched:
            offers = matched
        elif len(offers) != 1 or identities[0] is False:
            raise ListingImportError("parse_error", "предложение не удалось связать с объявлением")
    if len(offers) != 1:
        raise ListingImportError("parse_error", "в объявлении несколько подходящих предложений")
    return offers[0]


def _image_urls(images: Any, og_images: list[str], page_url: str | None) -> tuple[str, ...]:
    """Валидные снимки в исходном порядке, с og:image как запасным источником."""
    candidates: list[Any] = []
    for image in images if isinstance(images, list) else [images]:
        if isinstance(image, dict):
            candidates.extend((image.get("url"), image.get("contentUrl")))
        else:
            candidates.append(image)
    valid = dict.fromkeys(url for item in candidates if (url := https_url(item, page_url=page_url)))
    if not valid:
        valid = dict.fromkeys(url for item in og_images if (url := https_url(item, page_url=page_url)))
    return tuple(valid)


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


def extract_listing_fields(html: str, *, target: ListingUrl | None = None) -> dict[str, Any]:
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

    candidates: list[dict[str, Any]] = []
    for block in collector.json_ld:
        if len(block) > MAX_JSON_LD_LENGTH:
            continue
        try:
            data = json.loads(block)
        except (ValueError, RecursionError):
            continue
        candidates.extend(item for item in _walk(data) if _types(item) & _VEHICLE_TYPES)

    if not candidates:
        if looks_blocked(html):
            raise ListingImportError("source_forbidden", "страница проверки или блокировки")
        raise ListingImportError("parse_error", "нет структурированных данных автомобиля")

    vehicle, offer = _select_vehicle(candidates, target)
    markup = DromPageMarkup()
    if target is not None and collector.page_urls:
        identities = [_identity({"url": url}, target) for url in collector.page_urls]
        if not all(identity is True for identity in identities):
            raise ListingImportError("parse_error", "канонический URL страницы относится к другому объявлению")
        markup.feed(html)
        markup.close()
    availability = str(offer.get("availability") or "").rsplit("/", 1)[-1].lower()
    if availability in _UNAVAILABLE:
        raise ListingImportError("listing_unavailable", "объявление снято с продажи")

    currency = offer.get("priceCurrency")
    is_ruble = isinstance(currency, str) and currency.strip().upper() in {"RUB", "RUR"}
    engine = _first(vehicle.get("vehicleEngine"))
    engine = engine if isinstance(engine, dict) else {}
    page_url = target.canonical if target else None
    images = _image_urls(markup.images, [], page_url) or _image_urls(
        vehicle.get("image"), collector.og_images, page_url
    )
    fuel, _, volume = (markup.value("engine") or "").partition(",")
    mileage = vehicle.get("mileageFromOdometer")
    displacement = engine.get("engineDisplacement")
    power = engine.get("enginePower")

    return {
        "listing_id": (target.listing_id if target else None) or vehicle.get("sku") or vehicle.get("productID"),
        "brand": _name(vehicle.get("brand")) or _name(vehicle.get("manufacturer")),
        "model": _name(vehicle.get("model")),
        "year": vehicle.get("vehicleModelDate") or vehicle.get("productionDate")
        or vehicle.get("modelDate") or markup.value("year"),
        "price": offer.get("price") if is_ruble else None,
        "mileage_km": _quantity(
            markup.value("mileage_km") if mileage is None else mileage, _MILEAGE_UNITS
        ),
        "trim": vehicle.get("vehicleConfiguration") or markup.value("trim"),
        "fuel_type": _name(engine.get("fuelType")) or _name(vehicle.get("fuelType")) or fuel or None,
        "engine_volume_l": _quantity(volume if displacement is None else displacement, _VOLUME_UNITS),
        "power_hp": _quantity(markup.value("power_hp") if power is None else power, _POWER_UNITS),
        "transmission": _transmission(vehicle.get("vehicleTransmission")) or markup.value("transmission"),
        "drive_type": _drive(vehicle.get("driveWheelConfiguration")) or markup.value("drive_type"),
        "city": _city(vehicle, offer) or markup.value("city"),
        "image_url": images[0] if images else None,
        "image_urls": images,
        "market_valuation": None,
    }
