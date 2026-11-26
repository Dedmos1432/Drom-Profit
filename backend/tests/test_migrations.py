import pytest
from alembic import command
from sqlalchemy import inspect, text
from sqlalchemy.exc import IntegrityError

from tests.test_money_migration import migrate


def test_migrated_schema_matches_models(sql_engine):
    from alembic.autogenerate import compare_metadata
    from alembic.migration import MigrationContext

    from app.models import Base

    with sql_engine.connect() as conn:
        assert conn.scalar(text("SELECT version_num FROM alembic_version")) == "0003_car_gallery"
        assert compare_metadata(MigrationContext.configure(conn), Base.metadata) == []
        inspector = inspect(conn)
        for table in ("expenses", "benefits", "notes"):
            fk, = inspector.get_foreign_keys(table)
            assert fk["referred_table"] == "cars" and fk["options"]["ondelete"] == "CASCADE"
        checks = {
            "cars": {"ck_cars_price_money", "ck_cars_market_valuation_money", "ck_cars_gallery"},
            "expenses": {"ck_expenses_amount_money"},
            "benefits": {"ck_benefits_amount_money"},
        }
        for table, required in checks.items():
            assert required <= {item["name"] for item in inspector.get_check_constraints(table)}


def test_gallery_migrates_existing_photos_and_rolls_back_to_first_photo(sql_engine):
    first, second = "https://images.example.test/first.jpg", "https://images.example.test/second.jpg"
    unsafe_legacy = "urn:legacy:photo"
    migrate(sql_engine, command.downgrade, "0002_money_contract")
    with sql_engine.begin() as conn:
        conn.execute(text("INSERT INTO sessions (id, token_hash) VALUES ('gallery', 'synthetic')"))
        conn.execute(text("""
            INSERT INTO cars (id, session_id, source, brand, model, price, image_url,
                              expenses_reviewed, benefits_reviewed)
            VALUES ('photo', 'gallery', 'manual', 'Demo', 'Car', 100, :first, false, false),
                   ('empty', 'gallery', 'manual', 'Demo', 'Car', 100, NULL, false, false),
                   ('legacy', 'gallery', 'manual', 'Demo', 'Car', 100, :legacy, false, false)
        """), {"first": first, "legacy": unsafe_legacy})

    migrate(sql_engine, command.upgrade, "head")
    with sql_engine.begin() as conn:
        assert dict(conn.execute(text("SELECT id, image_urls FROM cars")).all()) == {
            "photo": [first], "empty": [], "legacy": [unsafe_legacy],
        }
        conn.execute(text("""
            UPDATE cars SET image_url = :main, image_urls = CAST(:gallery AS varchar[]) WHERE id = 'photo'
        """), {"main": second, "gallery": [second, first]})
        assert conn.scalar(text("SELECT image_urls FROM cars WHERE id = 'photo'")) == [second, first]

    with pytest.raises(IntegrityError) as error, sql_engine.begin() as conn:
        conn.execute(text("UPDATE cars SET image_url = :first WHERE id = 'photo'"), {"first": first})
    assert error.value.orig.diag.constraint_name == "ck_cars_gallery"

    migrate(sql_engine, command.downgrade, "0002_money_contract")
    with sql_engine.connect() as conn:
        assert "image_urls" not in {column["name"] for column in inspect(conn).get_columns("cars")}
        assert conn.scalar(text("SELECT image_url FROM cars WHERE id = 'photo'")) == second
    migrate(sql_engine, command.upgrade, "head")
    with sql_engine.connect() as conn:
        assert dict(conn.execute(text("SELECT id, image_urls FROM cars")).all()) == {
            "photo": [second], "empty": [], "legacy": [unsafe_legacy],
        }
