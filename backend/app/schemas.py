from datetime import datetime
from typing import Annotated, Literal

from pydantic import (
    AfterValidator,
    BaseModel,
    BeforeValidator,
    ConfigDict,
    Field,
    StrictBool,
    StrictInt,
    ValidationError,
    field_validator,
    model_validator,
)
from typing_extensions import TypedDict

from .formats import (
    IMAGE_COUNT_LIMIT, IMAGE_URL_LIMIT, validate_image_reference, validate_timestamp, validate_uri,
)
from .money import ADJUSTED_COST_MIN, COST_MAX, DIFFERENCE_MAX, MONEY_LIMIT


MAX_INT64 = (1 << 63) - 1
Price = Annotated[StrictInt, Field(ge=1, le=MONEY_LIMIT, json_schema_extra={"format": "int64"})]
MoneyAmount = Annotated[StrictInt, Field(ge=0, le=MONEY_LIMIT, json_schema_extra={"format": "int64"})]
AcquisitionCost = Annotated[StrictInt, Field(ge=1, le=COST_MAX, json_schema_extra={"format": "int64"})]
AdjustedCost = Annotated[
    StrictInt, Field(ge=ADJUSTED_COST_MIN, le=COST_MAX, json_schema_extra={"format": "int64"})
]
ComparisonDifference = Annotated[
    StrictInt, Field(ge=0, le=DIFFERENCE_MAX, json_schema_extra={"format": "int64"})
]
NonnegativeInteger = Annotated[StrictInt, Field(ge=0, le=MAX_INT64)]
Year = Annotated[StrictInt, Field(ge=1886, le=MAX_INT64)]
EngineVolume = Annotated[float, Field(strict=True, ge=0, allow_inf_nan=False)]
Nonempty = Annotated[str, Field(min_length=1)]
Source = Literal["drom", "demo", "manual"]
ExpenseKind = Literal["repair", "maintenance", "missing_equipment", "other"]
NoteKind = Literal["report", "inspection", "seller", "accepted_defect", "other"]


def require_nonblank(value: str) -> str:
    if not value.strip():
        raise ValueError("Требуется непустой текст")
    return value


Nonblank = Annotated[Nonempty, AfterValidator(require_nonblank)]
Uri = Annotated[str, Field(json_schema_extra={"format": "uri"}), AfterValidator(validate_uri)]
ImageReference = Annotated[str, Field(max_length=IMAGE_URL_LIMIT), AfterValidator(validate_image_reference)]
ImageReferences = Annotated[
    list[ImageReference],
    Field(max_length=IMAGE_COUNT_LIMIT),
    AfterValidator(lambda values: list(dict.fromkeys(values))),
]
Timestamp = Annotated[
    str,
    BeforeValidator(lambda value: value.isoformat() if isinstance(value, datetime) else value),
    AfterValidator(validate_timestamp),
    Field(json_schema_extra={"format": "date-time"}),
]


class Schema(BaseModel):
    model_config = ConfigDict(extra="forbid", from_attributes=True)


def check_photo_consistency(data):
    if isinstance(data, dict) and "image_url" in data and isinstance(photos := data.get("image_urls"), list):
        if data["image_url"] != (photos[0] if photos else None):
            raise ValueError("image_url должен совпадать с первой фотографией")
    return data


def readable_image_references(values: list[str]) -> list[str]:
    photos = []
    for value in values:
        try:
            photos.append(validate_image_reference(value))
        except ValueError:
            # До 0.3.0 карточки могли хранить произвольные ссылки.
            continue
    return list(dict.fromkeys(photos))


class ListingFields(Schema):
    source: Source
    listing_id: str | None
    year: Year | None
    mileage_km: NonnegativeInteger | None
    trim: str | None
    fuel_type: str | None
    engine_volume_l: EngineVolume | None
    power_hp: NonnegativeInteger | None
    transmission: str | None
    drive_type: str | None
    city: str | None
    image_url: ImageReference | None
    listing_url: Uri | None
    market_valuation: Price | None
    fetched_at: Timestamp | None


class ListingData(ListingFields):
    brand: str | None
    model: str | None
    price: Price | None
    image_urls: ImageReferences

    _check_photos = model_validator(mode="before")(check_photo_consistency)


class CarInput(ListingFields):
    brand: Nonblank
    model: Nonblank
    price: Price
    listing_id: str | None = None
    year: Year | None = None
    mileage_km: NonnegativeInteger | None = None
    trim: str | None = None
    fuel_type: str | None = None
    engine_volume_l: EngineVolume | None = None
    power_hp: NonnegativeInteger | None = None
    transmission: str | None = None
    drive_type: str | None = None
    city: str | None = None
    image_url: ImageReference | None = None
    image_urls: ImageReferences = Field(default_factory=list)
    listing_url: Uri | None = None
    market_valuation: Price | None = None
    fetched_at: Timestamp | None = None

    _check_photos = model_validator(mode="before")(check_photo_consistency)


class CarPatch(TypedDict, total=False):
    __pydantic_config__ = ConfigDict(extra="forbid")

    brand: Nonblank
    model: Nonblank
    price: Price
    year: Year | None
    mileage_km: NonnegativeInteger | None
    trim: str | None
    fuel_type: str | None
    engine_volume_l: EngineVolume | None
    power_hp: NonnegativeInteger | None
    transmission: str | None
    drive_type: str | None
    city: str | None
    image_url: ImageReference | None
    image_urls: ImageReferences
    listing_url: Uri | None
    market_valuation: Price | None
    expenses_reviewed: StrictBool
    benefits_reviewed: StrictBool


CheckedCarPatch = Annotated[CarPatch, BeforeValidator(check_photo_consistency)]


class BenefitInput(Schema):
    title: Nonblank
    amount: MoneyAmount | None
    factor_key: str | None = None
    comment: str | None = None


class ExpenseInput(BenefitInput):
    kind: ExpenseKind


class BenefitPatch(TypedDict, total=False):
    __pydantic_config__ = ConfigDict(extra="forbid")

    title: Nonblank
    amount: MoneyAmount | None
    factor_key: str | None
    comment: str | None


class ExpensePatch(BenefitPatch, total=False):
    kind: ExpenseKind


class Benefit(BenefitInput):
    title: Nonempty
    id: str
    factor_key: str | None
    comment: str | None


class Expense(ExpenseInput):
    title: Nonempty
    id: str
    factor_key: str | None
    comment: str | None


class NoteInput(Schema):
    kind: NoteKind
    text: Nonblank


class NotePatch(TypedDict, total=False):
    __pydantic_config__ = ConfigDict(extra="forbid")

    kind: NoteKind
    text: Nonblank


class Note(NoteInput):
    text: Nonempty
    id: str


class Car(ListingFields):
    id: str
    brand: Nonempty
    model: Nonempty
    price: Price
    expenses_reviewed: bool
    benefits_reviewed: bool
    expenses: list[Expense]
    benefits: list[Benefit]
    notes: list[Note]

    @field_validator("listing_url", "image_url", mode="wrap")
    @classmethod
    def read_existing_uri(cls, value, handler):
        # Older releases could persist malformed URIs. These optional links are unavailable.
        try:
            return handler(value)
        except ValidationError:
            return None


class CarDetail(Car):
    image_urls: ImageReferences

    @field_validator("image_urls", mode="before")
    @classmethod
    def read_existing_gallery(cls, values):
        return readable_image_references(values)

    @model_validator(mode="after")
    def set_main_photo(self):
        self.image_url = self.image_urls[0] if self.image_urls else None
        return self


class Session(Schema):
    token: str
    token_type: Literal["bearer"]


class ImportRequest(Schema):
    url: str = Field(json_schema_extra={"format": "uri"})


class Summary(Schema):
    car_id: str
    listing_price: Price
    total_expenses: MoneyAmount
    total_benefits: MoneyAmount
    acquisition_cost: AcquisitionCost
    adjusted_cost: AdjustedCost
    assessment_status: Literal["complete", "incomplete"]
    unknown_expenses: Annotated[int, Field(ge=0)]
    unknown_benefits: Annotated[int, Field(ge=0)]


class ComparisonRequest(Schema):
    car_ids: list[str] = Field(min_length=2, json_schema_extra={"uniqueItems": True})

    @field_validator("car_ids")
    @classmethod
    def unique_car_ids(cls, ids: list[str]) -> list[str]:
        if len(ids) != len(set(ids)):
            raise ValueError("Идентификаторы автомобилей должны быть уникальными")
        return ids


class ComparisonItem(Summary):
    difference_from_min: ComparisonDifference | None


class Warning(Schema):
    code: Literal["incomplete", "double_count", "negative_adjusted_cost"]
    message: str
    factor_key: str | None = None


class Comparison(Schema):
    items: list[ComparisonItem]
    warnings: list[Warning]
    provisional: bool


class Error(Schema):
    code: str
    message: str
