"""Run explicitly after the functional suite: pytest -q -s tests/load_postgres.py."""

import http.client
import json
import math
import os
import platform
import socket
import statistics
import time
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
from importlib.metadata import version
from threading import Barrier, Lock, Thread

import pytest
import uvicorn
from sqlalchemy import event, text

from app.main import create_app


@pytest.fixture
def http_server(db_url):
    app = create_app(db_url)
    server = uvicorn.Server(uvicorn.Config(
        app, host="127.0.0.1", access_log=False, log_level="error",
    ))
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        thread = Thread(target=server.run, kwargs={"sockets": [sock]}, daemon=True)
        thread.start()
        try:
            deadline = time.monotonic() + 15
            while not server.started:
                if not thread.is_alive() or time.monotonic() > deadline:
                    pytest.fail("Uvicorn startup failed")
                time.sleep(0.02)
            db_conflicts = Counter()
            counter_lock = Lock()

            @event.listens_for(app.state.engine, "handle_error")
            def record_conflict(context):
                state = getattr(context.original_exception, "sqlstate", None)
                if state in {"55P03", "40001", "40P01"}:
                    with counter_lock:
                        db_conflicts[state] += 1

            yield sock.getsockname()[1], db_conflicts
        finally:
            server.should_exit = True
            thread.join(timeout=30)
            assert not thread.is_alive()


class RequestFailed(Exception):
    pass


def virtual_user(port, start, rounds):
    samples = []
    errors = Counter()
    completed = 0
    connection = http.client.HTTPConnection("127.0.0.1", port, timeout=20)

    def call(method, path, status, payload=None, token=None):
        headers = {"Content-Type": "application/json"}
        if token:
            headers["Authorization"] = "Bearer " + token
        begin = time.perf_counter()
        try:
            connection.request(method, path, json.dumps(payload) if payload is not None else None, headers)
            response = connection.getresponse()
            body = json.loads(response.read())
            if response.status != status:
                errors[f"{response.status}/{body.get('code', 'unknown')}"] += 1
                raise RequestFailed
            return body
        except (OSError, http.client.HTTPException, ValueError) as exc:
            errors[type(exc).__name__] += 1
            connection.close()
            raise RequestFailed from exc
        finally:
            samples.append(1000 * (time.perf_counter() - begin))

    start.wait(timeout=30)
    try:
        for _ in range(rounds):
            try:
                token = call("POST", "/api/session", 201)["token"]
                card = {"brand": "Demo", "model": "A", "price": 100000, "source": "manual"}
                a = call("POST", "/api/cars", 201, card, token)["id"]
                b = call("POST", "/api/cars", 201, card | {"price": 105000}, token)["id"]
                path = f"/api/cars/{a}"
                call("POST", path + "/expenses", 201, {"kind": "repair", "title": "Ремонт", "amount": 9000}, token)
                call("POST", path + "/benefits", 201, {"title": "Резина", "amount": 3000}, token)
                flags = {"expenses_reviewed": True, "benefits_reviewed": True}
                call("PATCH", path, 200, flags, token)
                call("PATCH", f"/api/cars/{b}", 200, flags, token)
                result = call("GET", path + "/summary", 200, token=token)
                comparison = call("POST", "/api/comparison", 200, {"car_ids": [a, b]}, token)
                if (result["adjusted_cost"] != 106000 or comparison["provisional"]
                        or [row["difference_from_min"] for row in comparison["items"]] != [1000, 0]):
                    errors["invalid_result"] += 1
                else:
                    completed += 1
            except RequestFailed:
                continue
    finally:
        connection.close()
    return samples, errors, completed


@pytest.mark.parametrize("users", [1, 10, 50, 100])
def test_load(http_server, sql_engine, users):
    http_port, db_conflicts = http_server
    rounds = int(os.environ.get("DROM_LOAD_ROUNDS", "5"))
    assert rounds > 0
    start = Barrier(users + 1)
    with ThreadPoolExecutor(max_workers=users) as pool:
        futures = [pool.submit(virtual_user, http_port, start, rounds) for _ in range(users)]
        begin = time.perf_counter()
        start.wait(timeout=30)
        results = [future.result() for future in futures]
        elapsed = time.perf_counter() - begin
    samples = sorted(t for durations, _, _ in results for t in durations)
    errors = sum((counts for _, counts, _ in results), Counter())
    completed = sum(count for _, _, count in results)
    with sql_engine.connect() as conn:
        pg_version = conn.scalar(text("SHOW server_version"))
    conflicts = sum(count for key, count in errors.items() if key.startswith("409/") or key in {
        "422/duplicate_factor", "422/conflicting_equipment",
    })
    print(json.dumps({
        "users": users, "rounds_per_user": rounds, "workflows_completed": completed,
        "requests": len(samples), "seconds": elapsed, "requests_per_second": len(samples) / elapsed,
        "mean_ms": statistics.mean(samples), "median_ms": statistics.median(samples),
        "p95_ms": samples[math.ceil(len(samples) * .95) - 1],
        "p99_ms": samples[math.ceil(len(samples) * .99) - 1],
        "success_fraction": 1 - sum(errors.values()) / len(samples),
        "errors": dict(errors), "error_count": sum(errors.values()), "public_conflicts": conflicts,
        "db_conflicts_by_sqlstate": dict(db_conflicts),
        "pool_size": int(os.getenv("DROM_DB_POOL_SIZE", "10")),
        "max_overflow": int(os.getenv("DROM_DB_MAX_OVERFLOW", "5")),
        "workers": 1, "cpu_count": os.cpu_count(), "platform": platform.platform(),
        "python": platform.python_version(), "postgres": pg_version,
        "sqlalchemy": version("sqlalchemy"), "psycopg": version("psycopg"),
    }, ensure_ascii=False))
    assert not errors and completed == users * rounds
