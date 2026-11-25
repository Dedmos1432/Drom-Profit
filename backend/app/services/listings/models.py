"""Результат импорта — структура ListingData из ``shared/openapi.yaml``.

Модуль намеренно не зависит от ``app.schemas`` (Pydantic-модели Core Backend):
dataclass проверяет типы и ограничения контракта сам, а Core Backend
преобразует результат через ``schemas.ListingData.model_validate(data.as_dict())``.
"""

import logging
from dataclasses import asdict, dataclass, fields
from datetime import datetime
from typing import Any, Literal

from ...money import MONEY_LIMIT

logger = logging.getLogger(__name__)

ListingSource = Literal["drom", "demo"]
SOURCES = ("drom", "demo")  # manual задается только клиентом, импорт его не выдает

_INT_FIELDS = {"year": 1886, "price": 1, "mileage_km": 0, "power_hp": 0}
_STR_FIELDS = (
    "listing_id", "brand", "model", "trim", "fuel_type", "transmission", "drive_type", "city",
    "image_url", "listing_url", "fetched_at",
)


@dataclass(frozen=True, slots=True, kw_only=True)
class ListingData:
    """Предпросмотр объявления. Все неизвестные сведения — ``None``.

    Цены и оценки — целые рубли. ``market_valuation`` — оценка, опубликованная
    самим источником, модуль ее не рассчитывает.
    """

    source: ListingSource
    listing_id: str | None = None
    brand: str | None = None
    model: str | None = None
    year: int | None = None
    price: int | None = None
    mileage_km: int | None = None
    trim: str | None = None
    fuel_type: str | None = None
    engine_volume_l: float | None = None
    power_hp: int | None = None
    transmission: str | None = None
    drive_type: str | None = None
    city: str | None = None
    image_url: str | None = None
    listing_url: str | None = None
    market_valuation: int | None = None
    fetched_at: str | None = None

    def __post_init__(self) -> None:
        if self.source not in SOURCES:
            raise ValueError(f"source должен быть одним из {SOURCES}")
        for name, minimum in _INT_FIELDS.items():
            value = getattr(self, name)
            if value is None:
                continue
            if type(value) is not int or value < minimum:
                raise ValueError(f"{name}: ожидается целое >= {minimum}")
        if self.price is not None and self.price > MONEY_LIMIT:
            raise ValueError("price: сумма выше предела")
        if self.market_valuation is not None and (
            type(self.market_valuation) is not int or not 1 <= self.market_valuation <= MONEY_LIMIT
        ):
            logger.info("listing market valuation ignored: invalid amount")
            object.__setattr__(self, "market_valuation", None)
        for name in _STR_FIELDS:
            value = getattr(self, name)
            if value is not None and (type(value) is not str or not value.strip()):
                raise ValueError(f"{name}: ожидается непустая строка или null")
        volume = self.engine_volume_l
        if volume is not None and (type(volume) not in (int, float) or not 0 <= volume < 100):
            raise ValueError("engine_volume_l: ожидается число литров >= 0")
        for name in ("image_url", "listing_url"):
            value = getattr(self, name)
            if value is not None and not value.startswith("https://"):
                raise ValueError(f"{name}: ожидается https URL")
        if self.fetched_at is not None:
            try:
                parsed = datetime.fromisoformat(self.fetched_at)
            except ValueError:
                raise ValueError("fetched_at: ожидается дата и время RFC 3339") from None
            if parsed.tzinfo is None:
                raise ValueError("fetched_at: требуется часовой пояс")

    def as_dict(self) -> dict[str, Any]:
        """JSON-совместимый словарь со всеми 18 полями контракта в порядке схемы."""
        return asdict(self)

    @classmethod
    def from_dict(cls, row: dict[str, Any]) -> "ListingData":
        """Строгое создание из словаря: лишние или пропущенные ключи — ошибка."""
        names = {field.name for field in fields(cls)}
        if set(row) != names:
            extra, missing = set(row) - names, names - set(row)
            raise ValueError(f"Неверный набор полей: лишние {sorted(extra)}, нет {sorted(missing)}")
        return cls(**row)
