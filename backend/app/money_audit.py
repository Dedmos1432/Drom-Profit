"""Read-only inventory for a database before applying the money contract."""

import json

from sqlalchemy import Connection, create_engine, text
from sqlalchemy.pool import NullPool

from .db import database_url
from .money import JS_SAFE_INTEGER, MONEY_LIMIT


AUDIT_SQL = text("""
WITH expense_totals AS (
    SELECT car_id, sum(amount) AS total, max(amount) AS largest
    FROM expenses GROUP BY car_id
), benefit_totals AS (
    SELECT car_id, sum(amount) AS total, max(amount) AS largest
    FROM benefits GROUP BY car_id
), amounts AS (
    SELECT c.session_id, c.price AS p, c.market_valuation AS m,
           coalesce(x.total, 0::numeric) AS r, coalesce(b.total, 0::numeric) AS b,
           x.largest AS largest_expense, b.largest AS largest_benefit
    FROM cars c
    LEFT JOIN expense_totals x ON x.car_id = c.id
    LEFT JOIN benefit_totals b ON b.car_id = c.id
), values_by_car AS (
    SELECT *, p::numeric + r AS c, p::numeric + r - b AS e FROM amounts
), spreads AS (
    SELECT max(e) - min(e) AS max_difference
    FROM values_by_car GROUP BY session_id HAVING count(*) >= 2
), limits AS (
    SELECT CAST(:money_limit AS numeric) AS cap, CAST(:js_safe_integer AS numeric) AS js
)
SELECT count(*) AS cars,
       count(*) FILTER (WHERE p NOT BETWEEN 1 AND cap) AS price_outside_cap,
       count(*) FILTER (WHERE m IS NOT NULL AND m NOT BETWEEN 1 AND cap) AS valuation_outside_cap,
       count(*) FILTER (WHERE largest_expense > cap) AS expense_outside_cap,
       count(*) FILTER (WHERE largest_benefit > cap) AS benefit_outside_cap,
       count(*) FILTER (WHERE r > cap) AS total_expenses_outside_cap,
       count(*) FILTER (WHERE b > cap) AS total_benefits_outside_cap,
       count(*) FILTER (
           WHERE p > js OR abs(m::numeric) > js OR r > js OR b > js OR c > js OR abs(e) > js
       ) AS cars_with_unsafe_js_money,
       (SELECT count(*) FROM spreads CROSS JOIN limits WHERE max_difference > js) AS sessions_with_unsafe_js_spread
FROM values_by_car CROSS JOIN limits
""")


def audit_money(connection: Connection, limit: int = MONEY_LIMIT) -> dict[str, int]:
    return dict(connection.execute(
        AUDIT_SQL, {"money_limit": limit, "js_safe_integer": JS_SAFE_INTEGER},
    ).mappings().one())


def has_violations(result: dict[str, int]) -> bool:
    return any(count for name, count in result.items() if name != "cars")


def main() -> int:
    engine = create_engine(
        database_url(), isolation_level="REPEATABLE READ", poolclass=NullPool,
        hide_parameters=True,
    ).execution_options(postgresql_readonly=True)
    try:
        with engine.begin() as connection:
            connection.execute(text("SET LOCAL statement_timeout = '30s'"))
            result = audit_money(connection)
        print(json.dumps(result, sort_keys=True))
        return int(has_violations(result))
    finally:
        engine.dispose()


if __name__ == "__main__":
    raise SystemExit(main())
