"""Enforce the approved money contract after auditing existing cards."""

from alembic import op

from app.money_audit import AUDIT_SQL, audit_money, has_violations


revision = "0002_money_contract"
down_revision = "0001_core"
branch_labels = None
depends_on = None

# Freeze the contract bounds in this revision.
_LIMIT = 1_000_000_000_000
_CONSTRAINTS = (
    ("cars", "ck_cars_price_money", f"price BETWEEN 1 AND {_LIMIT}"),
    ("cars", "ck_cars_market_valuation_money", f"market_valuation BETWEEN 1 AND {_LIMIT}"),
    ("expenses", "ck_expenses_amount_money", f"amount BETWEEN 0 AND {_LIMIT}"),
    ("benefits", "ck_benefits_amount_money", f"amount BETWEEN 0 AND {_LIMIT}"),
)
_FAILURE = "Money contract audit failed. Run python -m app.money_audit on the old schema."


def upgrade():
    # Block writers until both the cross-row audit and the CHECKs are in place.
    op.execute("LOCK TABLE cars, expenses, benefits IN SHARE ROW EXCLUSIVE MODE")
    if op.get_context().as_sql:
        audit = AUDIT_SQL.bindparams(
            money_limit=_LIMIT, js_safe_integer=9_007_199_254_740_991,
        ).compile(dialect=op.get_context().dialect, compile_kwargs={"literal_binds": True})
        op.execute(f"""
            DO $audit$
            BEGIN
                IF EXISTS (
                    SELECT 1 FROM ({audit}) AS audit
                    WHERE EXISTS (
                        SELECT 1 FROM jsonb_each_text(to_jsonb(audit) - 'cars')
                        WHERE value <> '0'
                    )
                ) THEN RAISE EXCEPTION '{_FAILURE}';
                END IF;
            END $audit$;
        """)
    else:
        result = audit_money(op.get_bind(), _LIMIT)
        if has_violations(result):
            raise RuntimeError(f"{_FAILURE} Counts: {result}")
    for table, name, condition in _CONSTRAINTS:
        op.create_check_constraint(name, table, condition)


def downgrade():
    for table, name, _ in reversed(_CONSTRAINTS):
        op.drop_constraint(name, table, type_="check")
