from concurrent.futures import ThreadPoolExecutor
from hashlib import sha256

import pytest
from sqlalchemy import text
from fastapi.testclient import TestClient

from app.main import create_app


ITEMS = [
    ("expenses", {"title": "Ремонт", "kind": "repair", "amount": None}, {"amount": 0}),
    ("benefits", {"title": "Резина", "amount": 5000}, {"comment": "Осмотрено"}),
    ("notes", {"kind": "inspection", "text": "Царапина"}, {"text": "Уточнено"}),
]


@pytest.mark.parametrize("collection,body,patch", ITEMS)
def test_item_crud_and_isolation(client, headers, new_session, new_car, collection, body, patch):
    other_headers = new_session()
    car = new_car()
    same_owner_car = new_car()
    other_car = new_car(other_headers)
    response = client.post(f"{car}/{collection}", headers=headers, json=body)
    assert response.status_code == 201
    item = response.json()
    path = f"{car}/{collection}/{item['id']}"
    assert client.get(car, headers=headers).json()[collection] == [item]

    for candidate, owner in [
        (car, other_headers),
        (same_owner_car, headers),
        (other_car, other_headers),
    ]:
        cross = f"{candidate}/{collection}/{item['id']}"
        assert client.patch(cross, headers=owner, json=patch).status_code == 404
        assert client.delete(cross, headers=owner).status_code == 404
    assert client.post(f"{car}/{collection}", headers=other_headers, json=body).status_code == 404

    response = client.patch(path, headers=headers, json=patch)
    assert response.status_code == 200
    assert response.json() == item | patch
    assert client.get(car, headers=headers).json()[collection] == [item | patch]
    response = client.delete(path, headers=headers)
    assert response.status_code == 204
    assert response.content == b""
    assert client.get(car, headers=headers).json()[collection] == []


def test_car_isolation_and_comparison_access(client, headers, new_session, new_car):
    other_headers = new_session()
    car = new_car()
    other_car = new_car(other_headers)
    car_id = car.rsplit("/", 1)[1]
    other_id = other_car.rsplit("/", 1)[1]
    assert [row["id"] for row in client.get("/api/cars", headers=headers).json()] == [car_id]
    assert [row["id"] for row in client.get("/api/cars", headers=other_headers).json()] == [other_id]

    unknown = client.get("/api/cars/missing", headers=headers)
    for response in [
        client.get(other_car, headers=headers),
        client.patch(other_car, headers=headers, json={"price": 1}),
        client.delete(other_car, headers=headers),
        client.get(f"{other_car}/summary", headers=headers),
        client.post("/api/comparison", headers=headers, json={"car_ids": [car_id, other_id]}),
    ]:
        assert response.status_code == unknown.status_code == 404
        assert response.json() == unknown.json()
    assert client.get(other_car, headers=other_headers).json()["price"] == 100000


@pytest.mark.parametrize("authorization", [None, "Bearer", "Bearer wrong", "Basic invalid"])
def test_requires_valid_bearer(client, authorization):
    headers = {} if authorization is None else {"Authorization": authorization}
    response = client.get("/api/cars", headers=headers)
    assert response.status_code == 401
    assert response.headers["www-authenticate"] == "Bearer"
    assert set(response.json()) == {"code", "message"}


def test_persistence_and_cascade(db_url, sql_engine):
    with TestClient(create_app(db_url)) as client:
        token = client.post("/api/session").json()["token"]
        headers = {"Authorization": f"Bearer {token}"}
        car = client.post(
            "/api/cars", headers=headers,
            json={"source": "manual", "brand": "Toyota", "model": "Camry", "price": 100},
        ).json()
        path = f"/api/cars/{car['id']}"
        for collection, body, _ in ITEMS:
            assert client.post(f"{path}/{collection}", headers=headers, json=body).status_code == 201

    with sql_engine.connect() as connection:
        assert connection.execute(text("SELECT token_hash FROM sessions")).fetchone() == (
            sha256(token.encode()).hexdigest(),
        )
    with TestClient(create_app(db_url)) as restarted:
        saved = restarted.get(path, headers=headers).json()
        for collection, _, _ in ITEMS:
            assert len(saved[collection]) == 1
        assert restarted.delete(path, headers=headers).status_code == 204
        assert restarted.get(path, headers=headers).status_code == 404
    with sql_engine.connect() as connection:
        for collection, _, _ in ITEMS:
            assert connection.execute(text(f"SELECT COUNT(*) FROM {collection}")).fetchone() == (0,)


def test_explicit_review_and_editing_flags(client, headers, new_car):
    car = new_car(city="Владивосток")
    summary_path = f"{car}/summary"
    reviewed = {"expenses_reviewed": True, "benefits_reviewed": True}
    initial = client.get(summary_path, headers=headers).json()
    assert initial["assessment_status"] == "incomplete"
    assert initial["total_expenses"] == initial["unknown_expenses"] == 0
    explicit = client.patch(car, headers=headers, json=reviewed)
    assert explicit.status_code == 200
    assert client.get(summary_path, headers=headers).json()["assessment_status"] == "complete"

    expense = client.post(
        f"{car}/expenses", headers=headers, json=ITEMS[0][1]
    ).json()
    expense_path = f"{car}/expenses/{expense['id']}"
    current = client.get(car, headers=headers).json()
    assert not current["expenses_reviewed"] and current["benefits_reviewed"]
    client.patch(car, headers=headers, json=reviewed)
    summary = client.get(summary_path, headers=headers).json()
    assert summary["total_expenses"] == 0
    assert summary["unknown_expenses"] == 1
    assert summary["assessment_status"] == "incomplete"

    assert client.patch(expense_path, headers=headers, json={"amount": 0}).status_code == 200
    summary = client.get(summary_path, headers=headers).json()
    assert summary["unknown_expenses"] == summary["total_expenses"] == 0
    assert summary["assessment_status"] == "incomplete"
    client.patch(car, headers=headers, json=reviewed)
    assert client.get(summary_path, headers=headers).json()["assessment_status"] == "complete"

    assert client.delete(expense_path, headers=headers).status_code == 204
    assert client.get(car, headers=headers).json()["expenses_reviewed"] is False
    same_patch = client.patch(car, headers=headers, json={"city": None, "benefits_reviewed": True})
    assert same_patch.json()["city"] is None
    assert same_patch.json()["brand"] == "Toyota"
    assert same_patch.json()["benefits_reviewed"] is True
    assert same_patch.json()["expenses_reviewed"] is False
    client.patch(car, headers=headers, json=reviewed)
    assert client.patch(car, headers=headers, json={}).json()["expenses_reviewed"] is True
    updated = client.patch(car, headers=headers, json={"price": 110000}).json()
    assert not updated["expenses_reviewed"] and not updated["benefits_reviewed"]


def test_unknown_benefit_and_notes(client, headers, new_car):
    car = new_car()
    reviewed = {"expenses_reviewed": True, "benefits_reviewed": True}
    client.patch(car, headers=headers, json=reviewed)
    note_id = client.post(f"{car}/notes", headers=headers, json=ITEMS[2][1]).json()["id"]
    path = f"{car}/notes/{note_id}"
    assert client.patch(path, headers=headers, json={"kind": "report"}).status_code == 200
    assert client.delete(path, headers=headers).status_code == 204
    assert client.get(f"{car}/summary", headers=headers).json()["assessment_status"] == "complete"
    benefit = client.post(f"{car}/benefits", headers=headers, json=ITEMS[1][1] | {"amount": None})
    assert benefit.status_code == 201
    card = client.get(car, headers=headers).json()
    assert card["expenses_reviewed"] and not card["benefits_reviewed"]
    client.patch(car, headers=headers, json=reviewed)
    summary = client.get(f"{car}/summary", headers=headers).json()
    assert summary["unknown_benefits"] == 1
    assert summary["total_benefits"] == 0
    assert summary["assessment_status"] == "incomplete"


def test_equipment_conflicts_and_rollback(client, headers, new_car):
    first, second = new_car(), new_car()
    missing = {"title": "Резина", "amount": 15000, "kind": "missing_equipment", "factor_key": "tires"}
    benefit = {"title": "Резина", "amount": 10000, "factor_key": "tires"}
    assert client.post(f"{first}/expenses", headers=headers, json=missing).status_code == 201
    client.patch(first, headers=headers, json={"expenses_reviewed": True, "benefits_reviewed": True})
    duplicate = client.post(f"{first}/expenses", headers=headers, json=missing)
    assert duplicate.status_code == 422
    assert duplicate.json()["code"] == "duplicate_factor"
    conflict = client.post(f"{first}/benefits", headers=headers, json=benefit)
    assert conflict.status_code == 422
    assert conflict.json()["code"] == "conflicting_equipment"
    card = client.get(first, headers=headers).json()
    assert len(card["expenses"]) == 1 and card["benefits"] == []
    assert card["expenses_reviewed"] and card["benefits_reviewed"]

    added = client.post(f"{second}/benefits", headers=headers, json=benefit)
    assert added.status_code == 201
    conflict = client.post(f"{second}/expenses", headers=headers, json=missing)
    assert conflict.status_code == 422
    expense = client.post(
        f"{second}/expenses", headers=headers, json=missing | {"factor_key": "other"}
    ).json()
    rejected = client.patch(
        f"{second}/expenses/{expense['id']}", headers=headers, json={"factor_key": "tires"}
    )
    assert rejected.status_code == 422
    assert client.get(second, headers=headers).json()["expenses"][0]["factor_key"] == "other"

    ids = [path.rsplit("/", 1)[1] for path in (second, first)]
    comparison = client.post("/api/comparison", headers=headers, json={"car_ids": ids})
    assert comparison.status_code == 200
    result = comparison.json()
    assert result["provisional"] is True
    assert [row["car_id"] for row in result["items"]] == ids
    assert all(row["difference_from_min"] is None for row in result["items"])
    assert {warning["code"] for warning in result["warnings"]} == {"double_count", "incomplete"}


def test_simultaneous_factor_creation(client, headers, new_car):
    path = new_car() + "/benefits"

    def create():
        return client.post(path, headers=headers, json={"title": "Резина", "amount": 0, "factor_key": "tires"})

    with ThreadPoolExecutor(max_workers=2) as executor:
        first, second = executor.submit(create), executor.submit(create)
        assert sorted([first.result().status_code, second.result().status_code]) == [201, 422]
