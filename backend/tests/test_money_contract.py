import json
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from threading import Barrier

import pytest

from app.money import MONEY_LIMIT


VECTORS = json.loads(
    (Path(__file__).parents[2] / "shared/fixtures/money-contract-tests.json").read_text("utf-8")
)
CAR = {"source": "manual", "brand": "Демо", "model": "Проверка"}
FLAGS = {"expenses_reviewed": True, "benefits_reviewed": True}
GROUPS = ("expenses", "benefits")


def call_with_literal(client, headers, method, path, data, field, literal):
    content = json.dumps(data)
    if literal is not None:
        content = content[:-1] + ("," if data else "") + json.dumps(field) + ":" + literal + "}"
    return client.request(
        method, path, content=content, headers=headers | {"Content-Type": "application/json"},
    )


def factor_body(group, amount=0):
    return {"title": "Фактор", "amount": amount} | ({"kind": "repair"} if group == "expenses" else {})


def check(result, status, code=None):
    assert result.status_code == status, result.text
    if code is not None:
        assert result.json()["code"] == code


@pytest.mark.parametrize("row", VECTORS["inputs"], ids=lambda row: row["id"])
def test_shared_money_literals(client, headers, new_car, row):
    assert VECTORS["limit"] == MONEY_LIMIT
    cases = row["expect"]
    literal = row["literal"]
    codes = VECTORS["http"]
    created = call_with_literal(client, headers, "POST", "/api/cars", CAR, "price", literal)
    check(created, codes[cases["price"] or "price_success"], cases["price"])
    if cases["price"] is not None:
        assert client.get("/api/cars", headers=headers).json() == []
    else:
        assert created.json()["price"] == json.loads(literal)

    car = new_car()
    check(client.patch(car, headers=headers, json=FLAGS), 200)
    valuation = call_with_literal(client, headers, "PATCH", car, {}, "market_valuation", literal)
    check(valuation, codes[cases["market_valuation"] or "market_valuation_success"], cases["market_valuation"])
    if cases["market_valuation"] is None and literal is not None:
        assert valuation.json()["market_valuation"] == json.loads(literal)

    for group in GROUPS:
        body = factor_body(group)
        del body["amount"]
        check(client.patch(car, headers=headers, json=FLAGS), 200)
        added = call_with_literal(client, headers, "POST", car + "/" + group, body, "amount", literal)
        check(added, codes[cases["amount"] or "factor_success"], cases["amount"])
        current = client.get(car, headers=headers).json()
        if cases["amount"] is None:
            assert current[group][0]["amount"] == json.loads(literal)
            assert current[group + "_reviewed"] is False
        else:
            assert current[group] == []
            assert current[group + "_reviewed"] is True


def test_money_bounds_do_not_apply_to_nonmonetary_integers(client, headers):
    response = client.post("/api/cars", headers=headers, json=CAR | {
        "price": 1, "mileage_km": MONEY_LIMIT + 1,
    })
    check(response, 201)
    assert response.json()["mileage_km"] == MONEY_LIMIT + 1


def test_structure_takes_precedence_over_money_range(client, headers, new_car):
    example = VECTORS["mixed"]
    car = new_car()
    content = (
        '{"price":' + example["price_literal"]
        + ',"market_valuation":' + example["market_valuation_literal"] + '}'
    )
    check(client.patch(car, headers=headers, json=FLAGS), 200)
    response = client.patch(
        car, headers=headers | {"Content-Type": "application/json"}, content=content,
    )
    check(response, example["status"], example["code"])
    assert client.get(car, headers=headers).json()["expenses_reviewed"] is True


@pytest.mark.parametrize("group", GROUPS)
def test_shared_aggregate_bound_and_atomic_rejection(client, headers, new_car, group):
    case = VECTORS["aggregates"]
    car = new_car()
    path = f"{car}/{group}"
    first = client.post(path, headers=headers, json=factor_body(group, case["first"])).json()
    second = client.post(path, headers=headers, json=factor_body(group, case["remainder"])).json()
    first_path = f"{path}/{first['id']}"
    second_path = f"{path}/{second['id']}"
    check(client.patch(car, headers=headers, json=FLAGS), 200)
    prior = client.get(car, headers=headers).json()
    check(client.patch(first_path, headers=headers, json={"amount": case["first"]}), 200)
    check(client.post(path, headers=headers, json=factor_body(group, case["overflow"])),
          case["reject_status"], case["reject_code"])
    check(client.patch(first_path, headers=headers, json={"amount": case["first"] + 1}),
          case["reject_status"], case["reject_code"])
    assert client.get(car, headers=headers).json() == prior
    for amount in (None, 0, 1):
        check(client.patch(first_path, headers=headers, json={"amount": amount}), 200)
        assert client.get(car, headers=headers).json()[group + "_reviewed"] is False
    check(client.delete(second_path, headers=headers), 204)
    current = client.get(car, headers=headers).json()
    assert [item["amount"] for item in current[group]] == [1]
    summary = client.get(car + "/summary", headers=headers).json()
    assert summary["total_" + group] == 1


def test_shared_derived_bounds_and_negative_zero(client, headers, new_car):
    case = VECTORS["derivatives"]
    high, low = new_car(price=case["max_price"]), new_car(price=case["min_price"])
    check(client.post(high + "/expenses", headers=headers, json=factor_body("expenses", case["max_expenses"])), 201)
    check(client.post(low + "/benefits", headers=headers, json=factor_body("benefits", case["max_benefits"])), 201)
    ids = [path.rsplit("/", 1)[1] for path in (high, low)]
    result = client.post("/api/comparison", headers=headers, json={"car_ids": ids})
    check(result, 200)
    first, second = result.json()["items"]
    assert (first["acquisition_cost"], first["adjusted_cost"], second["adjusted_cost"]) == (
        case["acquisition_cost"], case["adjusted_max"], case["adjusted_min"],
    )
    assert [first["difference_from_min"], second["difference_from_min"]] == [case["difference_max"], 0]
    assert result.json()["provisional"] is True
    assert {w["code"] for w in result.json()["warnings"]} == {"incomplete", "negative_adjusted_cost"}
    item = client.get(low, headers=headers).json()["benefits"][0]
    check(client.patch(f"{low}/benefits/{item['id']}", headers=headers, json={"amount": 1}), 200)
    assert client.get(low + "/summary", headers=headers).json()["adjusted_cost"] == 0


@pytest.mark.parametrize("group", GROUPS)
@pytest.mark.parametrize("methods", [("POST", "POST"), ("POST", "PATCH"), ("PATCH", "PATCH")])
def test_concurrent_money_increments_respect_group_cap(client, headers, new_car, group, methods):
    car = new_car()
    path = f"{car}/{group}"
    targets = []
    for method in methods:
        if method == "PATCH":
            prior = client.post(path, headers=headers, json=factor_body(group))
            check(prior, 201)
            targets.append(f"{path}/{prior.json()['id']}")
        else:
            targets.append(path)
    check(client.patch(car, headers=headers, json=FLAGS), 200)
    start = Barrier(2, timeout=5)

    def change(method, target):
        body = factor_body(group, MONEY_LIMIT) if method == "POST" else {"amount": MONEY_LIMIT}
        start.wait()
        return client.request(method, target, headers=headers, json=body)

    with ThreadPoolExecutor(max_workers=2) as pool:
        calls = [pool.submit(change, method, target) for method, target in zip(methods, targets)]
        responses = [future.result(timeout=10) for future in calls]
    assert sum(response.status_code in (200, 201) for response in responses) == 1
    check(next(response for response in responses if response.status_code == 422), 422, "amount_out_of_range")
    updated = client.get(car, headers=headers).json()
    assert sum(row["amount"] for row in updated[group]) == MONEY_LIMIT
    assert updated[group + "_reviewed"] is False


@pytest.mark.parametrize("group", GROUPS)
def test_rejected_money_change_races_review_without_touching_flags(
    client, headers, new_car, new_session, group,
):
    car = new_car()
    stranger = new_session()
    other_car = new_car(owner=stranger)
    check(client.post(car + "/" + group, headers=headers, json=factor_body(group, MONEY_LIMIT)), 201)
    start = Barrier(2, timeout=5)

    def overflow():
        start.wait()
        return client.post(car + "/" + group, headers=headers, json=factor_body(group, 1))

    with ThreadPoolExecutor(max_workers=1) as pool:
        rejected = pool.submit(overflow)
        start.wait()
        check(client.patch(car, headers=headers, json={group + "_reviewed": True}), 200)
        check(rejected.result(timeout=10), 422, "amount_out_of_range")
    assert client.get(car, headers=headers).json()[group + "_reviewed"] is True
    check(client.post(other_car + "/" + group, headers=stranger, json=factor_body(group, MONEY_LIMIT)), 201)
    check(client.post(car + "/" + group, headers=stranger, json=factor_body(group, 1)), 404)
