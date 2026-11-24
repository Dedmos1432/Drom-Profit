import sqlite3
from concurrent.futures import ThreadPoolExecutor
from threading import Barrier, Event

import pytest
from sqlalchemy import event


@pytest.mark.parametrize("block_commit", [False, True], ids=["begin", "commit"])
def test_busy_rolls_back_and_next_request_succeeds(client, db_path, headers, new_car, block_commit):
    path = new_car()
    client.patch(path, headers=headers, json={"expenses_reviewed": True})

    # Shorten only the test's wait on the API connections.
    @event.listens_for(client.app.state.engine, "checkout")
    def short_wait(connection, _record, _proxy):
        connection.execute("PRAGMA busy_timeout=50")

    factor = {"title": "Осмотр", "kind": "other", "amount": 10}
    with sqlite3.connect(db_path) as blocker:
        if block_commit:
            blocker.execute("BEGIN")
            blocker.execute("SELECT price FROM cars").fetchall()
        else:
            blocker.execute("BEGIN IMMEDIATE")
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


def test_short_writer_lock_allows_waiting_request_to_finish(client, db_path, headers, new_car):
    path = new_car()
    starting_write = Event()

    @event.listens_for(client.app.state.engine, "before_cursor_execute")
    def notify_begin(_conn, _cursor, sql, _params, _context, _many):
        if sql == "BEGIN IMMEDIATE":
            starting_write.set()

    with ThreadPoolExecutor(max_workers=1) as pool:
        with sqlite3.connect(db_path) as blocker:
            blocker.execute("BEGIN IMMEDIATE")
            update = pool.submit(client.patch, path, headers=headers, json={"price": 777})
            assert starting_write.wait(timeout=3)
        assert update.result(timeout=6).status_code == 200
    assert client.get(path, headers=headers).json()["price"] == 777
