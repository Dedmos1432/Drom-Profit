"""Persist the ordered gallery while keeping the existing main photo."""

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects.postgresql import ARRAY


revision = "0003_car_gallery"
down_revision = "0002_money_contract"
branch_labels = None
depends_on = None


def upgrade():
    op.add_column(
        "cars", sa.Column("image_urls", ARRAY(sa.String()), nullable=False,
                          server_default=sa.text("'{}'::varchar[]")),
    )
    op.execute("UPDATE cars SET image_urls = ARRAY[image_url] WHERE image_url IS NOT NULL")
    op.create_check_constraint(
        "ck_cars_gallery", "cars",
        "cardinality(image_urls) <= 64 AND array_position(image_urls, NULL) IS NULL "
        "AND image_url IS NOT DISTINCT FROM image_urls[1]",
    )


def downgrade():
    # The old schema has room for the main photo only.
    op.drop_constraint("ck_cars_gallery", "cars", type_="check")
    op.drop_column("cars", "image_urls")
