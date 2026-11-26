import asyncio
from concurrent.futures import ThreadPoolExecutor
from threading import Event, Lock

import anyio
import fastapi.routing
import pytest
from fastapi.testclient import TestClient

from app import schemas
from app.api import cars
from app.main import create_app
from app.services.listing_import import adapt_parser
from app.services.listings.importer import make_importer


DEMO_URL = "https://auto.drom.ru/demo/1.html"


@pytest.fixture
def one_connection(monkeypatch):
    monkeypatch.setenv("DROM_DB_POOL_SIZE", "1")
    monkeypatch.setenv("DROM_DB_MAX_OVERFLOW", "0")


def test_read_responses_release_the_snapshot_before_validation(client, new_car, headers, monkeypatch):
    photos = [f"https://images.example.test/gallery/{index}.jpg" for index in range(28)]
    first, second = new_car(image_urls=photos), new_car()
    original = fastapi.routing.serialize_response
    engine = client.app.state.engine

    async def serialize(*args, **kwargs):
        assert engine.pool.checkedout() == 0
        return await original(*args, **kwargs)

    monkeypatch.setattr(fastapi.routing, "serialize_response", serialize)
    for path in ("/api/cars", first + "/summary"):
        assert client.get(path, headers=headers).status_code == 200
    detail = client.get(first, headers=headers)
    assert detail.status_code == 200 and detail.json()["image_urls"] == photos
    ids = [path.rsplit("/", 1)[-1] for path in (first, second)]
    assert client.post("/api/comparison", headers=headers, json={"car_ids": ids}).status_code == 200


def test_slow_summary_validation_does_not_starve_an_independent_request(
    one_connection, client, new_car, headers, monkeypatch
):
    car = new_car()
    reached, release = Event(), Event()
    original = fastapi.routing.serialize_response

    async def serialize(*args, **kwargs):
        if isinstance(kwargs.get("response_content"), schemas.Summary):
            reached.set()
            assert await anyio.to_thread.run_sync(release.wait, 8)
        return await original(*args, **kwargs)

    monkeypatch.setattr(fastapi.routing, "serialize_response", serialize)
    with ThreadPoolExecutor(max_workers=1) as executor:
        pending = executor.submit(client.get, car + "/summary", headers=headers)
        try:
            assert reached.wait(5)
            assert client.app.state.engine.pool.checkedout() == 0
            assert client.post("/api/session").status_code == 201
            assert not pending.done()
        finally:
            release.set()
        assert pending.result(timeout=5).status_code == 200


@pytest.mark.parametrize(("collection", "payload"), [
    ("expenses", {"title": "Ремонт", "kind": "repair", "amount": 100}),
    ("benefits", {"title": "Оснащение", "amount": 100}),
    ("notes", {"kind": "inspection", "text": "Осмотр"}),
])
def test_noop_item_patch_releases_lock_before_validation(
    client, new_car, headers, monkeypatch, collection, payload
):
    car = new_car()
    response = client.post(f"{car}/{collection}", headers=headers, json=payload)
    assert response.status_code == 201
    item = response.json()
    reviewed = {"expenses_reviewed": True, "benefits_reviewed": True}
    assert client.patch(car, headers=headers, json=reviewed).status_code == 200
    original = fastapi.routing.serialize_response

    async def serialize(*args, **kwargs):
        assert client.app.state.engine.pool.checkedout() == 0
        return await original(*args, **kwargs)

    monkeypatch.setattr(fastapi.routing, "serialize_response", serialize)
    patch = client.patch(f"{car}/{collection}/{item['id']}", headers=headers, json=payload)
    assert patch.status_code == 200 and patch.json() == item
    current = client.get(car, headers=headers).json()
    assert {key: current[key] for key in reviewed} == reviewed


def test_snapshot_connection_recovers_after_exception(
    one_connection, db_url, new_car, headers, monkeypatch
):
    car = new_car()

    def fail_after_read(_car):
        raise RuntimeError("Injected calculation failure")

    monkeypatch.setattr(cars, "summarize", fail_after_read)
    with TestClient(create_app(db_url), raise_server_exceptions=False) as client:
        response = client.get(car + "/summary", headers=headers)
        assert response.status_code == 500 and response.json()["code"] == "internal_error"
        assert client.app.state.engine.pool.checkedout() == 0
        assert client.get(car, headers=headers).status_code == 200


def test_pool_recovers_when_connection_becomes_available(one_connection, client, monkeypatch):
    engine = client.app.state.engine
    monkeypatch.setattr(engine.pool, "_timeout", .1)
    with engine.connect():
        failed = client.post("/api/session")
        assert failed.status_code == 500 and failed.json()["code"] == "internal_error"
    assert engine.pool.checkedout() == 0
    assert client.post("/api/session").status_code == 201


def test_parallel_slow_demo_imports_leave_the_database_available(one_connection, db_url, monkeypatch):
    count = 16
    ready, release = Event(), Event()
    counter = Lock()
    entered = 0
    parser = make_importer()
    fetch_demo = parser.provider.fetch

    async def delayed_fetch(url):
        nonlocal entered
        with counter:
            entered += 1
            if entered == count:
                ready.set()
        assert await asyncio.to_thread(release.wait, 10)
        return await fetch_demo(url)

    monkeypatch.setattr(parser.provider, "fetch", delayed_fetch)
    with TestClient(create_app(db_url, importer=adapt_parser(parser))) as client:
        issued = client.post("/api/session")
        assert issued.status_code == 201
        headers = {"Authorization": "Bearer " + issued.json()["token"]}
        with ThreadPoolExecutor(max_workers=count) as executor:
            pending = [
                executor.submit(client.post, "/api/cars/import", headers=headers, json={"url": DEMO_URL})
                for _ in range(count)
            ]
            try:
                assert ready.wait(5)
                assert client.app.state.engine.pool.checkedout() == 0
                assert client.get("/api/cars", headers=headers).json() == []
            finally:
                release.set()
            for response in (item.result(timeout=5) for item in pending):
                assert response.status_code == 200 and response.json()["source"] == "demo"
        assert client.app.state.engine.pool.checkedout() == 0
