"""Limits for the MVP's integer values in rubles."""

MONEY_LIMIT = 1_000_000_000_000
COST_MAX = 2 * MONEY_LIMIT
ADJUSTED_COST_MIN = 1 - MONEY_LIMIT
DIFFERENCE_MAX = 3 * MONEY_LIMIT - 1
JS_SAFE_INTEGER = (1 << 53) - 1

MONEY_INPUT_FIELDS = frozenset(("price", "amount", "market_valuation"))
MONEY_LIMIT_LABEL = f"{MONEY_LIMIT:,}".replace(",", " ") + " ₽"
MONEY_RANGE_MESSAGE = f"Допустимый диапазон суммы зависит от поля. Максимум — {MONEY_LIMIT_LABEL}."
