import json
from pathlib import Path

import pytest

from app.models import Benefit, Car, Expense
from app.services.calculation import compare


VECTORS = Path(__file__).resolve().parents[2] / "shared/fixtures/calculation-tests.json"


@pytest.fixture(params=json.loads(VECTORS.read_text("utf-8")), ids=lambda case: case["name"])
def case(request):
    return request.param


def assert_comparison(result, case):
    expected = case["expected"]
    assert result["items"] == expected["items"]
    assert {warning["code"] for warning in result["warnings"]} == set(expected["warning_codes"])
    assert result["provisional"] is expected["provisional"]


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
    assert_comparison(compare(cars).model_dump(), case)


def test_shared_vectors_via_rest(case, client, headers, new_car):
    fixture_ids = {}
    for row in case["cars"]:
        path = new_car(price=row["price"])
        fixture_ids[path.rsplit("/", 1)[1]] = row["id"]
        for collection in ("expenses", "benefits"):
            for factor in row[collection]:
                payload = {"title": "Фактор"} | {k: v for k, v in factor.items() if k != "id"}
                response = client.post(f"{path}/{collection}", headers=headers, json=payload)
                assert response.status_code == 201, response.text
        flags = {key: row[key] for key in ("expenses_reviewed", "benefits_reviewed")}
        assert client.patch(path, headers=headers, json=flags).status_code == 200
        summary = client.get(f"{path}/summary", headers=headers)
        assert summary.status_code == 200
        expected = next(item for item in case["expected"]["items"] if item["car_id"] == row["id"])
        assert summary.json() | {"car_id": row["id"]} == {
            k: v for k, v in expected.items() if k != "difference_from_min"
        }
    response = client.post("/api/comparison", headers=headers, json={"car_ids": list(fixture_ids)})
    assert response.status_code == 200, response.text
    result = response.json()
    for item in result["items"]:
        item["car_id"] = fixture_ids[item["car_id"]]
    assert_comparison(result, case)
