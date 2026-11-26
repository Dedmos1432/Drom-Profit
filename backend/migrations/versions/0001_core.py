"""Initial PostgreSQL storage for the existing v0.1 REST contract."""

from alembic import op
import sqlalchemy as sa


revision = "0001_core"
down_revision = None
branch_labels = None
depends_on = None


def item_columns():
    return [
        sa.Column("id", sa.String(), primary_key=True),
        sa.Column("car_id", sa.String(), sa.ForeignKey("cars.id", ondelete="CASCADE"), nullable=False),
    ]


def factor_columns():
    return [
        *item_columns(),
        sa.Column("title", sa.String(), nullable=False),
        sa.Column("amount", sa.BigInteger()),
        sa.Column("factor_key", sa.String()),
        sa.Column("comment", sa.String()),
        sa.CheckConstraint("amount >= 0"),
    ]


def upgrade():
    op.create_table(
        "sessions",
        sa.Column("id", sa.String(), primary_key=True),
        sa.Column("token_hash", sa.String(64), nullable=False, unique=True),
    )
    op.create_table(
        "cars",
        sa.Column("id", sa.String(), primary_key=True),
        sa.Column("session_id", sa.String(), sa.ForeignKey("sessions.id"), nullable=False),
        sa.Column("source", sa.String(), nullable=False),
        sa.Column("listing_id", sa.String()),
        sa.Column("brand", sa.String(), nullable=False),
        sa.Column("model", sa.String(), nullable=False),
        sa.Column("year", sa.BigInteger()),
        sa.Column("price", sa.BigInteger(), nullable=False),
        sa.Column("mileage_km", sa.BigInteger()),
        sa.Column("trim", sa.String()),
        sa.Column("fuel_type", sa.String()),
        sa.Column("engine_volume_l", sa.Double()),
        sa.Column("power_hp", sa.BigInteger()),
        sa.Column("transmission", sa.String()),
        sa.Column("drive_type", sa.String()),
        sa.Column("city", sa.String()),
        sa.Column("image_url", sa.String()),
        sa.Column("listing_url", sa.String()),
        sa.Column("market_valuation", sa.BigInteger()),
        sa.Column("fetched_at", sa.String()),
        sa.Column("expenses_reviewed", sa.Boolean(), nullable=False),
        sa.Column("benefits_reviewed", sa.Boolean(), nullable=False),
        sa.CheckConstraint("price > 0"),
    )
    op.create_index("ix_cars_session_id", "cars", ["session_id"])
    op.create_table(
        "expenses", *factor_columns(), sa.Column("kind", sa.String(), nullable=False),
        sa.UniqueConstraint("car_id", "kind", "factor_key", name="uq_expenses_car_kind_key"),
    )
    op.create_table(
        "benefits", *factor_columns(),
        sa.UniqueConstraint("car_id", "factor_key", name="uq_benefits_car_key"),
    )
    op.create_table(
        "notes", *item_columns(),
        sa.Column("kind", sa.String(), nullable=False),
        sa.Column("text", sa.String(), nullable=False),
    )
    for table in ("expenses", "benefits", "notes"):
        op.create_index(f"ix_{table}_car_id", table, ["car_id"])


def downgrade():
    for table in ("notes", "benefits", "expenses", "cars", "sessions"):
        op.drop_table(table)
