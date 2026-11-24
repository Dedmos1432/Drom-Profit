from concurrent.futures import ThreadPoolExecutor
from threading import Barrier, Event

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import event, text
from sqlalchemy.exc import IntegrityError

from app.main import create_app


def lock_car(connection, path):
    connection.execute(text("SELECT id FROM cars WHERE id=:id FOR UPDATE"), {"id": path.split("/")[-1]})


def test_locked_car_rolls_back_and_next_request_succeeds(client, sql_engine, headers, new_car):
    path = new_car()
    client.patch(path, headers=headers, json={"expenses_reviewed": True})
    factor = {"title": "Осмотр", "kind": "other", "amount": 10}
    with sql_engine.begin() as blocker:
        lock_car(blocker, path)
        response = client.post(path + "/expenses", headers=headers, json=factor)
        assert response.status_code == 500
        assert "временно занята" in response.json()["message"]
    saved = client.get(path, headers=headers).json()
    assert saved["expenses"] == [] and saved["expenses_reviewed"]
    assert client.post(path + "/expenses", headers=headers, json=factor).status_code == 201
    saved = client.get(path, headers=headers).json()
    assert len(saved["expenses"]) == 1 and not saved["expenses_reviewed"]


def test_concurrent_opposing_factors(client, headers, new_car):
    path = new_car()
    flags = {"expenses_reviewed": True, "benefits_reviewed": True}
    assert client.patch(path, headers=headers, json=flags).status_code == 200
    start = Barrier(2, timeout=5)

    def add(group):
        body = {"title": "Резина", "amount": 10, "factor_key": "tires"}
        if group == "expenses":
            body["kind"] = "missing_equipment"
        start.wait()
        return client.post(f"{path}/{group}", headers=headers, json=body)

    with ThreadPoolExecutor(max_workers=2) as pool:
        responses = list(pool.map(add, ("expenses", "benefits")))
    assert sorted(r.status_code for r in responses) == [201, 422]
    assert next(r for r in responses if r.status_code == 422).json()["code"] == "conflicting_equipment"
    saved = client.get(path, headers=headers).json()
    for group, response in zip(("expenses", "benefits"), responses):
        assert saved[group] == ([response.json()] if response.status_code == 201 else [])
        assert saved[group + "_reviewed"] == (response.status_code != 201)


@pytest.mark.parametrize("different_owner", [False, True], ids=["two-cars", "two-users"])
def test_independent_writes(client, sql_engine, headers, new_car, new_session, different_owner):
    first = new_car()
    owner = new_session() if different_owner else headers
    second = new_car(owner=owner)
    waiting = Event()

    @event.listens_for(client.app.state.engine, "before_cursor_execute")
    def observe_lock(_conn, _cursor, statement, params, _context, _many):
        if "FOR UPDATE" in statement and first.split("/")[-1] in params.values():
            waiting.set()

    with ThreadPoolExecutor(max_workers=1) as pool:
        with sql_engine.begin() as blocker:
            lock_car(blocker, first)
            pending = pool.submit(client.patch, first, headers=headers, json={"price": 777})
            assert waiting.wait(timeout=5)
            assert client.patch(second, headers=owner, json={"price": 888}).status_code == 200
            assert not pending.done()
        assert pending.result(timeout=5).status_code == 200
    assert client.get(first, headers=headers).json()["price"] == 777


def test_comparison_uses_one_snapshot(client, headers, new_car):
    first, second = new_car(), new_car()
    ids = [p.split("/")[-1] for p in (first, second)]
    parents_read, proceed = Event(), Event()

    @event.listens_for(client.app.state.engine, "after_cursor_execute")
    def after_parent_select(_conn, _cursor, statement, _params, _context, _many):
        if "FROM cars" in statement and "cars.id IN (" in statement:
            parents_read.set()
            assert proceed.wait(timeout=10)

    with ThreadPoolExecutor(max_workers=1) as pool:
        old = pool.submit(client.post, "/api/comparison", json={"car_ids": ids}, headers=headers)
        try:
            assert parents_read.wait(timeout=5)
            expense = {"title": "Осмотр", "kind": "other", "amount": 50}
            assert client.post(first + "/expenses", json=expense, headers=headers).status_code == 201
        finally:
            proceed.set()
        response = old.result(timeout=5)
        assert response.status_code == 200
        assert response.json()["items"][0]["total_expenses"] == 0
    assert client.get(first + "/summary", headers=headers).json()["total_expenses"] == 50


def test_database_foreign_keys_and_atomicity(client, new_car, sql_engine):
    path = new_car()
    params = {"id": path.split("/")[-1]}
    with pytest.raises(IntegrityError) as caught:
        with sql_engine.begin() as conn:
            conn.execute(text("UPDATE cars SET price=123 WHERE id=:id"), params)
            conn.execute(text("INSERT INTO notes(id,car_id,kind,text) VALUES ('n','absent','other','x')"))
    assert caught.value.orig.sqlstate == "23503"
    with sql_engine.begin() as conn:
        assert conn.scalar(text("SELECT price FROM cars WHERE id=:id"), params) == 100000
        conn.execute(text("INSERT INTO notes(id,car_id,kind,text) VALUES ('n',:id,'other','x')"), params)
        conn.execute(text("DELETE FROM cars WHERE id=:id"), params)
        assert conn.scalar(text("SELECT count(*) FROM notes")) == 0


def test_concurrent_factor_edits_preserve_equipment_invariant(client, headers, new_car):
    path = new_car()
    body = {"title": "Резина", "amount": 100, "factor_key": None}
    expense = client.post(path + "/expenses", headers=headers, json=body | {"kind": "missing_equipment"})
    benefit = client.post(path + "/benefits", headers=headers, json=body)
    assert expense.status_code == benefit.status_code == 201
    start = Barrier(2, timeout=5)

    def patch(item_path):
        start.wait()
        return client.patch(item_path, headers=headers, json={"factor_key": "tires"})

    with ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(patch, [
            f"{path}/expenses/{expense.json()['id']}", f"{path}/benefits/{benefit.json()['id']}",
        ]))
    assert sorted(r.status_code for r in results) == [200, 422]
    assert next(r for r in results if r.status_code == 422).json()["code"] == "conflicting_equipment"
    car = client.get(path, headers=headers).json()
    assert {car[group][0]["factor_key"] for group in ("expenses", "benefits")} == {None, "tires"}


def test_failed_commit_undoes_factor_and_flag(sql_engine, db_url, headers, new_car):
    path = new_car()
    # A deferred FK in this test's private schema fails after the ORM flush.
    with sql_engine.begin() as conn:
        conn.execute(text("CREATE TABLE allowed_title (title varchar PRIMARY KEY)"))
        conn.execute(text("INSERT INTO allowed_title VALUES ('OK')"))
        conn.execute(text(
            "ALTER TABLE expenses ADD FOREIGN KEY (title) REFERENCES allowed_title(title) "
            "DEFERRABLE INITIALLY DEFERRED"
        ))
    with TestClient(create_app(db_url), raise_server_exceptions=False) as client:
        response = client.patch(path, headers=headers, json={"expenses_reviewed": True})
        assert response.status_code == 200
        body = {"title": "rejected", "kind": "repair", "amount": 10}
        response = client.post(path + "/expenses", headers=headers, json=body)
        assert response.status_code == 500 and response.json()["code"] == "internal_error"
        saved = client.get(path, headers=headers).json()
        assert saved["expenses"] == [] and saved["expenses_reviewed"]
        assert client.post(path + "/expenses", headers=headers, json=body | {"title": "OK"}).status_code == 201
