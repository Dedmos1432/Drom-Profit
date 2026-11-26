from pathlib import Path

import pytest
from alembic import command
from alembic.config import Config
from sqlalchemy import text
from sqlalchemy.exc import IntegrityError

from app.money import MONEY_LIMIT
from app.money_audit import audit_money, has_violations


def migrate(sql_engine, action, revision):
    config = Config(str(Path(__file__).parents[1] / "alembic.ini"))
    with sql_engine.begin() as connection:
        config.attributes["connection"] = connection
        action(config, revision)


def make_legacy_car(connection, price=1, valuation=None):
    connection.execute(text("INSERT INTO sessions (id, token_hash) VALUES ('session', 'synthetic')"))
    connection.execute(text("""
        INSERT INTO cars (
            id, session_id, source, brand, model, price, market_valuation,
            expenses_reviewed, benefits_reviewed
        )
        VALUES ('car', 'session', 'manual', 'Demo', 'Demo', :price, :valuation, false, false)
    """), {"price": price, "valuation": valuation})


def old_amount(connection, group, amount, number):
    columns, kind = (", kind", ", 'repair'") if group == "expenses" else ("", "")
    connection.execute(text(f"""
        INSERT INTO {group} (id, car_id, title, amount{columns})
        VALUES (:id, 'car', 'Synthetic', :amount{kind})
    """), {"id": group + str(number), "amount": amount})


def read_only_audit(sql_engine):
    engine = sql_engine.execution_options(isolation_level="REPEATABLE READ", postgresql_readonly=True)
    with engine.begin() as connection:
        assert connection.scalar(text("SHOW transaction_read_only")) == "on"
        return audit_money(connection)


def test_money_migration_round_trip_and_database_constraints(sql_engine):
    assert read_only_audit(sql_engine)["cars"] == 0
    migrate(sql_engine, command.downgrade, "0001_core")
    with sql_engine.begin() as connection:
        make_legacy_car(connection, MONEY_LIMIT, 1)
        old_amount(connection, "expenses", MONEY_LIMIT, 1)
        old_amount(connection, "benefits", None, 1)
    assert not has_violations(read_only_audit(sql_engine))
    for _ in range(2):
        migrate(sql_engine, command.upgrade, "head")
        with sql_engine.connect() as connection:
            state = connection.execute(text("SELECT price, market_valuation FROM cars")).one()
            assert tuple(state) == (MONEY_LIMIT, 1)
            assert connection.scalar(text("SELECT amount FROM expenses")) == MONEY_LIMIT
            assert connection.scalar(text("SELECT amount FROM benefits")) is None
        for statements, name, amount in [
            ("UPDATE cars SET price = :amount", "ck_cars_price_money", MONEY_LIMIT + 1),
            ("UPDATE cars SET market_valuation = :amount", "ck_cars_market_valuation_money", 0),
            ("UPDATE cars SET market_valuation = :amount", "ck_cars_market_valuation_money", MONEY_LIMIT + 1),
            ("UPDATE expenses SET amount = :amount", "ck_expenses_amount_money", MONEY_LIMIT + 1),
            ("UPDATE benefits SET amount = :amount", "ck_benefits_amount_money", MONEY_LIMIT + 1),
        ]:
            with pytest.raises(IntegrityError) as error, sql_engine.begin() as connection:
                connection.execute(text(statements), {"amount": amount})
            assert error.value.orig.diag.constraint_name == name
        migrate(sql_engine, command.downgrade, "0001_core")
    migrate(sql_engine, command.upgrade, "head")


@pytest.mark.parametrize(("group", "column"), [("expenses", "total_expenses"), ("benefits", "total_benefits")])
def test_upgrade_blocks_valid_individual_amounts_with_invalid_aggregate(sql_engine, group, column):
    migrate(sql_engine, command.downgrade, "0001_core")
    with sql_engine.begin() as connection:
        make_legacy_car(connection)
        old_amount(connection, group, MONEY_LIMIT, 1)
        old_amount(connection, group, 1, 2)
    before = read_only_audit(sql_engine)
    assert before[column + "_outside_cap"] == 1
    assert before[("expense" if group == "expenses" else "benefit") + "_outside_cap"] == 0
    with pytest.raises(RuntimeError, match="Money contract audit failed"):
        migrate(sql_engine, command.upgrade, "head")
    assert read_only_audit(sql_engine) == before
    with sql_engine.connect() as connection:
        assert connection.scalar(text("SELECT version_num FROM alembic_version")) == "0001_core"
        assert set(connection.scalars(text(f"SELECT amount FROM {group}"))) == {1, MONEY_LIMIT}


def test_audit_detects_invalid_columns_and_unsafe_comparison(sql_engine):
    migrate(sql_engine, command.downgrade, "0001_core")
    js_limit = (1 << 53) - 1
    with sql_engine.begin() as connection:
        make_legacy_car(connection, js_limit, 0)
        old_amount(connection, "expenses", js_limit, 1)
        old_amount(connection, "benefits", MONEY_LIMIT + 1, 1)
        connection.execute(text("""
            INSERT INTO cars (
                id, session_id, source, brand, model, price, expenses_reviewed, benefits_reviewed
            )
            VALUES ('other', 'session', 'manual', 'Demo', 'Demo', 1, false, false)
        """))
    report = read_only_audit(sql_engine)
    assert report == {
        "cars": 2, "price_outside_cap": 1, "valuation_outside_cap": 1,
        "expense_outside_cap": 1, "benefit_outside_cap": 1,
        "total_expenses_outside_cap": 1, "total_benefits_outside_cap": 1,
        "cars_with_unsafe_js_money": 1, "sessions_with_unsafe_js_spread": 1,
    }
    with pytest.raises(RuntimeError, match="Money contract audit failed"):
        migrate(sql_engine, command.upgrade, "head")
    assert read_only_audit(sql_engine) == report
