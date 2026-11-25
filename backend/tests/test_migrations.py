from sqlalchemy import inspect, text


def test_migrated_schema_matches_models(sql_engine):
    from alembic.autogenerate import compare_metadata
    from alembic.migration import MigrationContext

    from app.models import Base

    with sql_engine.connect() as conn:
        assert conn.scalar(text("SELECT version_num FROM alembic_version")) == "0002_money_contract"
        assert compare_metadata(MigrationContext.configure(conn), Base.metadata) == []
        inspector = inspect(conn)
        for table in ("expenses", "benefits", "notes"):
            fk, = inspector.get_foreign_keys(table)
            assert fk["referred_table"] == "cars" and fk["options"]["ondelete"] == "CASCADE"
        checks = {
            "cars": {"ck_cars_price_money", "ck_cars_market_valuation_money"},
            "expenses": {"ck_expenses_amount_money"},
            "benefits": {"ck_benefits_amount_money"},
        }
        for table, required in checks.items():
            assert required <= {item["name"] for item in inspector.get_check_constraints(table)}
