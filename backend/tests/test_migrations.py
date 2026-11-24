from sqlalchemy import inspect, text


def test_initial_migration_matches_models(sql_engine):
    from alembic.autogenerate import compare_metadata
    from alembic.migration import MigrationContext

    from app.models import Base

    with sql_engine.connect() as conn:
        assert conn.scalar(text("SELECT version_num FROM alembic_version")) == "0001_core"
        assert compare_metadata(MigrationContext.configure(conn), Base.metadata) == []
        inspector = inspect(conn)
        for table in ("expenses", "benefits", "notes"):
            fk, = inspector.get_foreign_keys(table)
            assert fk["referred_table"] == "cars" and fk["options"]["ondelete"] == "CASCADE"
        for table in ("expenses", "benefits", "cars"):
            assert inspector.get_check_constraints(table)
