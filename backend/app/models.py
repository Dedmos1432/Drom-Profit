from uuid import uuid4

from sqlalchemy import BigInteger, CheckConstraint, Double, ForeignKey, String, UniqueConstraint, text
from sqlalchemy.dialects.postgresql import ARRAY
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, relationship


from .formats import IMAGE_COUNT_LIMIT
from .money import MONEY_LIMIT


class Base(DeclarativeBase):
    type_annotation_map = {int: BigInteger(), float: Double()}


class UserSession(Base):
    __tablename__ = "sessions"

    id: Mapped[str] = mapped_column(primary_key=True, default=lambda: str(uuid4()))
    token_hash: Mapped[str] = mapped_column(String(64), unique=True)


class Car(Base):
    __tablename__ = "cars"
    __table_args__ = (
        CheckConstraint("price > 0"),
        CheckConstraint(f"price BETWEEN 1 AND {MONEY_LIMIT}", name="ck_cars_price_money"),
        CheckConstraint(
            f"market_valuation BETWEEN 1 AND {MONEY_LIMIT}", name="ck_cars_market_valuation_money"
        ),
        CheckConstraint(
            f"cardinality(image_urls) <= {IMAGE_COUNT_LIMIT} AND "
            "array_position(image_urls, NULL) IS NULL AND image_url IS NOT DISTINCT FROM image_urls[1]",
            name="ck_cars_gallery",
        ),
    )

    id: Mapped[str] = mapped_column(primary_key=True, default=lambda: str(uuid4()))
    session_id: Mapped[str] = mapped_column(ForeignKey("sessions.id"), index=True)
    source: Mapped[str]
    listing_id: Mapped[str | None]
    brand: Mapped[str]
    model: Mapped[str]
    year: Mapped[int | None]
    price: Mapped[int]
    mileage_km: Mapped[int | None]
    trim: Mapped[str | None]
    fuel_type: Mapped[str | None]
    engine_volume_l: Mapped[float | None]
    power_hp: Mapped[int | None]
    transmission: Mapped[str | None]
    drive_type: Mapped[str | None]
    city: Mapped[str | None]
    image_url: Mapped[str | None]
    image_urls: Mapped[list[str]] = mapped_column(
        ARRAY(String()), server_default=text("'{}'::varchar[]"), deferred=True,
    )
    listing_url: Mapped[str | None]
    market_valuation: Mapped[int | None]
    fetched_at: Mapped[str | None]
    expenses_reviewed: Mapped[bool] = mapped_column(default=False)
    benefits_reviewed: Mapped[bool] = mapped_column(default=False)
    expenses: Mapped[list["Expense"]] = relationship(
        lazy="selectin", cascade="all, delete-orphan", passive_deletes=True
    )
    benefits: Mapped[list["Benefit"]] = relationship(
        lazy="selectin", cascade="all, delete-orphan", passive_deletes=True
    )
    notes: Mapped[list["Note"]] = relationship(
        lazy="selectin", cascade="all, delete-orphan", passive_deletes=True
    )


class CarItem:
    id: Mapped[str] = mapped_column(primary_key=True, default=lambda: str(uuid4()))
    car_id: Mapped[str] = mapped_column(ForeignKey("cars.id", ondelete="CASCADE"), index=True)


class MonetaryFactor(CarItem):
    title: Mapped[str]
    amount: Mapped[int | None]
    factor_key: Mapped[str | None]
    comment: Mapped[str | None]


class Expense(MonetaryFactor, Base):
    __tablename__ = "expenses"
    __table_args__ = (
        CheckConstraint("amount >= 0"),
        CheckConstraint(f"amount BETWEEN 0 AND {MONEY_LIMIT}", name="ck_expenses_amount_money"),
        UniqueConstraint("car_id", "kind", "factor_key", name="uq_expenses_car_kind_key"),
    )

    kind: Mapped[str]


class Benefit(MonetaryFactor, Base):
    __tablename__ = "benefits"
    __table_args__ = (
        CheckConstraint("amount >= 0"),
        CheckConstraint(f"amount BETWEEN 0 AND {MONEY_LIMIT}", name="ck_benefits_amount_money"),
        UniqueConstraint("car_id", "factor_key", name="uq_benefits_car_key"),
    )


class Note(CarItem, Base):
    __tablename__ = "notes"

    kind: Mapped[str]
    text: Mapped[str]
