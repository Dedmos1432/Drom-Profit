import json
from pathlib import Path

import pytest

from app.models import Benefit, Car, Expense
from app.services.calculation import compare


VECTORS = Path(__file__).resolve().parents[2] / "shared/fixtures/calculation-tests.json"


@pytest.mark.parametrize(
    "case", json.loads(VECTORS.read_text("utf-8")), ids=lambda case: case["name"]
)
def test_shared_calculation_vectors(case):
    cars = [
        Car(
            id=row["id"],
            price=row["price"],
            expenses_reviewed=row["expenses_reviewed"],
            benefits_reviewed=row["benefits_reviewed"],
            expenses=[Expense(**expense) for expense in row["expenses"]],
            benefits=[Benefit(**benefit) for benefit in row["benefits"]],
        )
        for row in case["cars"]
    ]
    result = compare(cars)
    expected = case["expected"]
    assert {item.car_id: item.model_dump() for item in result.items} == {
        item["car_id"]: item for item in expected["items"]
    }
    assert {warning.code for warning in result.warnings} == set(expected["warning_codes"])
    assert result.provisional is expected["provisional"]
