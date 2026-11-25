"""Нормализация характеристик к формату ListingData.

Правила:
* каждое поле разбирается независимо; нераспознанное значение превращается в ``None``,
  а не в догадку;
* деньги, пробег, мощность — целые числа; объем двигателя — литры с одним знаком;
* тип топлива, коробка и привод приводятся к словарю ниже (формат demo-fixtures);
  значение вне словаря сохраняется очищенным текстом, чтобы не терять сведения.
"""

import math
import re
from collections.abc import Mapping
from datetime import date
from typing import Any
from urllib.parse import urljoin, urlsplit, urlunsplit

from .models import ListingData, ListingSource

_SPACES = re.compile(r"[\s   ]+")
_PLACEHOLDERS = {"", "-", "—", "–", "нет", "нет данных", "не указано", "не указан", "null", "none"}
_NUMBER = r"(\d+(?:[.,]\d+)?)"

FUEL_TYPES = {
    "Бензин": ("бензин", "бензиновый", "petrol", "gasoline"),
    "Дизель": ("дизель", "дизельный", "diesel", "дт"),
    "Гибрид": ("гибрид", "гибридный", "hybrid"),
    "Электро": ("электро", "электричество", "электрический", "electric", "ev"),
    "Газ": ("газ", "гбо", "газ/бензин", "lpg", "cng", "пропан", "метан"),
}
TRANSMISSIONS = {
    "Механика": ("механика", "механическая", "мкпп", "мт", "manual", "mt"),
    "Автомат": ("автомат", "автоматическая", "акпп", "ат", "automatic", "at"),
    "Робот": ("робот", "роботизированная", "ркпп", "amt", "robot", "dct", "dsg"),
    "Вариатор": ("вариатор", "cvt"),
}
DRIVE_TYPES = {
    "Передний": ("передний", "fwd", "front"),
    "Задний": ("задний", "rwd", "rear"),
    "Полный": ("полный", "4wd", "awd", "4x4", "4х4", "full"),
}


def _lookup(table: Mapping[str, tuple[str, ...]]) -> dict[str, str]:
    return {alias: canonical for canonical, aliases in table.items() for alias in aliases} | {
        canonical.lower(): canonical for canonical in table
    }


_FUEL, _TRANSMISSION, _DRIVE = map(_lookup, (FUEL_TYPES, TRANSMISSIONS, DRIVE_TYPES))


def clean_text(value: Any, max_length: int = 200) -> str | None:
    """Строка без лишних пробелов; пустые значения и заглушки — ``None``."""
    if not isinstance(value, str):
        return None
    text = _SPACES.sub(" ", value).strip()
    if text.lower().strip(".") in _PLACEHOLDERS or len(text) > max_length:
        return None
    if any(ord(ch) < 0x20 for ch in text):
        return None
    return text


def _compact(value: Any) -> str | None:
    text = clean_text(value)
    return None if text is None else _SPACES.sub("", text).lower()


def _plain_int(value: Any) -> int | None:
    if isinstance(value, bool):
        return None
    if isinstance(value, int):
        return value
    if isinstance(value, float) and math.isfinite(value) and value.is_integer():
        return int(value)
    return None


def parse_price(value: Any) -> int | None:
    """``1200000``, ``"1 200 000 ₽"``, ``"1200000 руб."`` → 1200000. Ноль и минус — ``None``."""
    number = _plain_int(value)
    if number is None:
        text = _compact(value)
        match = text and re.fullmatch(r"(\d{1,12})(?:₽|руб\.?|р\.?|rub)?", text)
        number = int(match.group(1)) if match else None
    return number if number is not None and number >= 1 else None


def parse_market_valuation(value: Any) -> int | None:
    """Оценка источника в рублях; формат как у цены."""
    return parse_price(value)


def parse_mileage(value: Any) -> int | None:
    """``145000``, ``"145 000 км"``, ``"145 тыс. км"`` → 145000.

    «Без пробега по РФ» и подобное — ``None``: фактический пробег неизвестен.
    """
    number = _plain_int(value)
    if number is not None:
        return number if 0 <= number <= 5_000_000 else None
    text = _compact(value)
    if text is None:
        return None
    match = re.fullmatch(rf"{_NUMBER}(тыс\.?)?(?:км\.?)?", text)
    if not match:
        return None
    amount = float(match.group(1).replace(",", "."))
    if match.group(2):
        amount *= 1000
    elif not amount.is_integer():
        return None
    result = round(amount)
    return result if 0 <= result <= 5_000_000 else None


def parse_year(value: Any, today: date | None = None) -> int | None:
    number = _plain_int(value)
    if number is None:
        text = _compact(value)
        match = text and re.fullmatch(r"(\d{4})(?:г\.?|года?)?", text)
        number = int(match.group(1)) if match else None
    latest = (today or date.today()).year + 1
    return number if number is not None and 1886 <= number <= latest else None


def parse_engine_volume(value: Any) -> float | None:
    """Литры с одним знаком: ``2.5``, ``"2,5 л"``, ``"2494 см³"`` → 2.5."""
    if isinstance(value, bool):
        return None
    if isinstance(value, (int, float)):
        liters = float(value)
        if liters >= 100:  # число без единиц больше 100 — кубические сантиметры
            liters /= 1000
    else:
        text = _compact(value)
        match = text and re.fullmatch(rf"{_NUMBER}(л\.?|l|см3|см³|куб\.?см\.?|cc)?", text)
        if not match:
            return None
        liters = float(match.group(1).replace(",", "."))
        unit = match.group(2) or ""
        if unit.startswith(("см", "куб", "cc")) or (not unit and liters >= 100):
            liters /= 1000
    if not math.isfinite(liters) or not 0 < liters < 20:
        return None
    return round(liters, 1)


def parse_power(value: Any) -> int | None:
    """Лошадиные силы: ``181``, ``"181 л.с."``; ``"133 кВт"`` пересчитывается в л.с."""
    number = _plain_int(value)
    if number is None:
        text = _compact(value)
        match = text and re.fullmatch(rf"{_NUMBER}(л\.?с\.?|hp|квт|kw)?", text)
        if not match:
            return None
        amount = float(match.group(1).replace(",", "."))
        if (match.group(2) or "") in ("квт", "kw"):
            amount *= 1.35962
        number = round(amount)
    return number if 0 < number <= 3000 else None


def _vocabulary(value: Any, table: Mapping[str, str]) -> str | None:
    text = clean_text(value, max_length=60)
    if text is None:
        return None
    key = text.lower().strip(" .")
    return table.get(key, text)


def normalize_fuel(value: Any) -> str | None:
    return _vocabulary(value, _FUEL)


def normalize_transmission(value: Any) -> str | None:
    return _vocabulary(value, _TRANSMISSION)


def normalize_drive(value: Any) -> str | None:
    return _vocabulary(value, _DRIVE)


def split_engine(value: Any) -> tuple[str | None, float | None]:
    """Строка Дрома вида ``"бензин, 2.5 л"`` → (``"Бензин"``, 2.5)."""
    text = clean_text(value)
    if text is None:
        return None, None
    fuel, volume = None, None
    for part in (piece.strip() for piece in text.split(",")):
        parsed_volume = parse_engine_volume(part)
        if parsed_volume is not None and volume is None:
            volume = parsed_volume
        elif part and fuel is None and not any(ch.isdigit() for ch in part):
            fuel = normalize_fuel(part)
    return fuel, volume


def https_url(value: Any, *, page_url: str | None = None) -> str | None:
    """Проверяет ссылку для передачи клиенту. Относительные адреса остаются на хосте страницы."""
    text = value.strip() if isinstance(value, str) else ""
    if (
        not text or len(text) > 2048 or not text.isascii() or "\\" in text
        or any(ch.isspace() or not 0x21 <= ord(ch) < 0x7F for ch in text)
    ):
        return None
    try:
        parts = urlsplit(text)
        if not parts.scheme:
            if not page_url or not parts.path:
                return None
            page_host = urlsplit(page_url).hostname
            parts = urlsplit(urljoin(page_url, text))
            if parts.hostname != page_host:
                return None
        if (
            parts.scheme != "https" or not parts.hostname or parts.netloc.lower() != parts.hostname
            or not re.fullmatch(r"[a-z0-9-]+(?:\.[a-z0-9-]+)+", parts.hostname)
        ):
            return None
        return urlunsplit(parts._replace(netloc=parts.hostname, fragment=""))
    except ValueError:
        return None


def normalize_listing(
    raw: Mapping[str, Any],
    *,
    source: ListingSource,
    listing_url: str | None,
    listing_id: str | None,
    fetched_at: str | None,
) -> ListingData:
    """Собирает ListingData из «сырых» полей источника.

    Ожидаемые ключи ``raw`` совпадают с полями ListingData; дополнительно
    поддерживается ``engine`` — строка «тип, объем».
    """
    engine_fuel, engine_volume = split_engine(raw.get("engine"))
    fuel = normalize_fuel(raw.get("fuel_type")) or engine_fuel
    volume = parse_engine_volume(raw.get("engine_volume_l"))
    raw_id = raw.get("listing_id")
    return ListingData(
        source=source,
        listing_id=clean_text(str(raw_id) if type(raw_id) is int else raw_id) or listing_id,
        brand=clean_text(raw.get("brand"), 80),
        model=clean_text(raw.get("model"), 80),
        year=parse_year(raw.get("year")),
        price=parse_price(raw.get("price")),
        mileage_km=parse_mileage(raw.get("mileage_km")),
        trim=clean_text(raw.get("trim")),
        fuel_type=fuel,
        engine_volume_l=volume if volume is not None else engine_volume,
        power_hp=parse_power(raw.get("power_hp")),
        transmission=normalize_transmission(raw.get("transmission")),
        drive_type=normalize_drive(raw.get("drive_type")),
        city=clean_text(raw.get("city"), 80),
        image_url=https_url(raw.get("image_url")),
        listing_url=listing_url,
        market_valuation=parse_market_valuation(raw.get("market_valuation")),
        fetched_at=fetched_at,
    )
