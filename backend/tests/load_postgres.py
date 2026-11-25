"""Explicit local load test: python -m pytest -q -s tests/load_postgres.py."""

import http.client
import json
import os
import platform
import resource
import socket
import time
from collections import Counter, defaultdict
from concurrent.futures import ThreadPoolExecutor
from importlib.metadata import version
from threading import Barrier, Thread

import pytest
import uvicorn
from sqlalchemy import text

from app.main import create_app
from tests.performance_probe import PerformanceProbe, distribution, endpoint


@pytest.fixture
def http_server(db_url, sql_engine, monkeypatch):
    app = create_app(db_url)
    probe = PerformanceProbe()
    tokens = int(os.environ.get("DROM_LOAD_THREAD_TOKENS", "0"))
    server = uvicorn.Server(uvicorn.Config(
        probe.bind(app, monkeypatch, tokens), host="127.0.0.1", access_log=False, log_level="error",
    ))
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        thread = Thread(target=server.run, kwargs={"sockets": [sock]}, daemon=True)
        thread.start()
        monitor = None
        try:
            deadline = time.monotonic() + 15
            while not server.started:
                if not thread.is_alive() or time.monotonic() > deadline:
                    pytest.fail("Uvicorn startup failed")
                time.sleep(0.02)
            engine = app.state.engine
            probe.instrument_engine(engine, monkeypatch)
            monitor = probe.watch_postgres(sql_engine)
            yield sock.getsockname()[1], engine, probe
        finally:
            probe.stop.set()
            if monitor is not None:
                monitor.join(timeout=5)
                assert not monitor.is_alive()
            server.should_exit = True
            thread.join(timeout=30)
            assert not thread.is_alive()


class RequestFailed(Exception):
    pass


def virtual_user(port, start, rounds):
    samples = defaultdict(list)
    statuses = Counter()
    errors = Counter()
    completed = 0
    connection = http.client.HTTPConnection("127.0.0.1", port, timeout=20)

    def call(method, path, status, payload=None, token=None):
        route = endpoint(method, path)
        headers = {"Content-Type": "application/json"}
        if token:
            headers["Authorization"] = "Bearer " + token
        begin = time.perf_counter()
        try:
            connection.request(method, path, json.dumps(payload) if payload is not None else None, headers)
            response = connection.getresponse()
            statuses[response.status] += 1
            body = json.loads(response.read())
            if response.status != status:
                errors[f"{route} {response.status}/{body.get('code', 'unknown')}"] += 1
                raise RequestFailed
            return body
        except (OSError, http.client.HTTPException, ValueError) as exc:
            errors[f"{route} {type(exc).__name__}"] += 1
            connection.close()
            raise RequestFailed from exc
        finally:
            samples[route].append(1000 * (time.perf_counter() - begin))

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
    return samples, statuses, errors, completed


def run_wave(http_server, sql_engine, users, rounds, wave):
    http_port, engine, probe = http_server
    probe.reset()
    start = Barrier(users + 1)
    with ThreadPoolExecutor(max_workers=users) as pool:
        futures = [pool.submit(virtual_user, http_port, start, rounds) for _ in range(users)]
        begin = time.perf_counter()
        cpu_start = time.process_time()
        start.wait(timeout=30)
        results = [future.result() for future in futures]
        elapsed = time.perf_counter() - begin
        cpu_seconds = time.process_time() - cpu_start
    by_endpoint = defaultdict(list)
    statuses, errors = Counter(), Counter()
    completed = 0
    for samples, codes, failures, count in results:
        for route, durations in samples.items():
            by_endpoint[route].extend(durations)
        statuses.update(codes)
        errors.update(failures)
        completed += count
    timings = distribution([t for durations in by_endpoint.values() for t in durations])
    with sql_engine.connect() as connection:
        pg_version = connection.scalar(text("SHOW server_version"))
    report = probe.report(engine, sql_engine)
    print(json.dumps({
        "users": users, "rounds_per_user": rounds, "wave": wave,
        "workflows_completed": completed, "requests": timings["count"],
        "seconds": elapsed, "requests_per_second": timings["count"] / elapsed,
        "p50_ms": timings["p50_ms"], "p95_ms": timings["p95_ms"],
        "p99_ms": timings["p99_ms"], "max_ms": timings["max_ms"],
        "status_codes": dict(statuses), "error_count": sum(errors.values()),
        "errors": dict(errors), "endpoint_latency": {
            route: distribution(values) for route, values in by_endpoint.items()
        },
        "pool_size": engine.pool.size(), "max_overflow": int(os.getenv("DROM_DB_MAX_OVERFLOW", "5")),
        "pool_timeout_seconds": engine.pool.timeout(), "workers": 1, "cpu_count": os.cpu_count(),
        "python_cpu_seconds": round(cpu_seconds, 3),
        "python_cpu_percent": round(100 * cpu_seconds / elapsed, 2),
        "python_peak_rss_kib": resource.getrusage(resource.RUSAGE_SELF).ru_maxrss,
        "platform": platform.platform(),
        "python": platform.python_version(), "postgres": pg_version,
        "framework_versions": {name: version(name) for name in (
            "fastapi", "starlette", "anyio", "uvicorn", "sqlalchemy", "psycopg",
        )},
        "diagnostics": report,
    }, ensure_ascii=False), flush=True)
    return (
        not errors and completed == users * rounds
        and report["after_wave"]["checked_out"] == report["after_wave"]["pool_waiters"] == 0
        and not report["after_wave"]["postgres"].get("idle in transaction")
    )


@pytest.mark.parametrize("users", [
    int(value) for value in os.environ.get("DROM_LOAD_CLIENTS", "1,10,25,50,75,100").split(",")
])
def test_load(http_server, sql_engine, users):
    rounds = int(os.environ.get("DROM_LOAD_ROUNDS", "5"))
    waves = int(os.environ.get("DROM_LOAD_WAVES", "1"))
    if not (1 <= users <= 150 and 1 <= rounds <= 20 and 1 <= waves <= 10):
        pytest.fail("Local load requires 1–150 clients, 1–20 rounds and 1–10 waves")
    results = [
        run_wave(http_server, sql_engine, users, rounds, wave) for wave in range(1, waves + 1)
    ]
    assert all(results)
