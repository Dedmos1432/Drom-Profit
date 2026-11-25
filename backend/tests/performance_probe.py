"""Local-only telemetry used by the explicit load_postgres.py run."""

import asyncio
import math
import time
from collections import Counter, defaultdict
from contextlib import suppress
from contextvars import ContextVar
from dataclasses import dataclass
from threading import Event, Lock, Thread

import anyio
import fastapi.routing
from sqlalchemy import event, text
from sqlalchemy.exc import TimeoutError as PoolTimeout
from starlette.exceptions import HTTPException


def endpoint(method, path):
    parts = path.split("/")
    if len(parts) >= 4 and parts[1:3] == ["api", "cars"] and parts[3] != "import":
        parts[3] = "{car_id}"
        if len(parts) == 6:
            parts[5] = "{item_id}"
    return method + " " + "/".join(parts)


def distribution(samples):
    ordered = sorted(samples)
    if not ordered:
        return {"count": 0}
    return {
        "count": len(ordered),
        "p50_ms": round(ordered[math.ceil(len(ordered) * .5) - 1], 3),
        "p95_ms": round(ordered[math.ceil(len(ordered) * .95) - 1], 3),
        "p99_ms": round(ordered[math.ceil(len(ordered) * .99) - 1], 3),
        "max_ms": round(ordered[-1], 3),
    }


ACTIVITY = text("""
    SELECT coalesce(state, 'unknown'), coalesce(wait_event_type, 'none'), count(*)
    FROM pg_stat_activity
    WHERE datname = current_database() AND pid <> pg_backend_pid()
      AND backend_type = 'client backend'
    GROUP BY state, wait_event_type
""")


def pg_activity(connection):
    result = Counter()
    for state, wait_type, count in connection.execute(ACTIVITY):
        result[state] += count
        if wait_type in ("Lock", "LWLock", "IO"):
            result["wait/" + wait_type] += count
    return result


@dataclass
class RequestPhase:
    endpoint: str
    serializing: bool = False


current_request: ContextVar[RequestPhase | None] = ContextVar("current_request", default=None)


class PerformanceProbe:
    def __init__(self):
        self.lock = Lock()
        self.held = {}
        self.waiting = 0
        self.stop = Event()
        self.reset()

    def reset(self):
        with self.lock:
            self.samples = defaultdict(list)
            self.peaks = Counter()
            self.errors = Counter()
            self.last_saturation = {}

    def bind(self, app, monkeypatch, tokens):
        original = fastapi.routing.serialize_response

        async def serialize(*args, **kwargs):
            request = current_request.get()
            if request is not None:
                request.serializing = True
            try:
                return await original(*args, **kwargs)
            finally:
                if request is not None:
                    request.serializing = False

        monkeypatch.setattr(fastapi.routing, "serialize_response", serialize)
        http_error = app.exception_handlers[HTTPException]

        async def record_http_error(request, error):
            cause = error.__cause__
            phase = current_request.get()
            if cause is not None and phase is not None:
                with self.lock:
                    self.errors["http_cause/" + phase.endpoint + "/" + type(cause).__name__] += 1
            return await http_error(request, error)

        app.add_exception_handler(HTTPException, record_http_error)

        async def instrumented(scope, receive, send):
            if scope["type"] == "lifespan":
                limiter = anyio.to_thread.current_default_thread_limiter()
                if tokens:
                    limiter.total_tokens = tokens
                self.thread_tokens = limiter.total_tokens
                task = asyncio.create_task(self.sample_loop(limiter))
                try:
                    await app(scope, receive, send)
                finally:
                    task.cancel()
                    with suppress(asyncio.CancelledError):
                        await task
                return
            if scope["type"] != "http":
                await app(scope, receive, send)
                return
            phase = RequestPhase(endpoint(scope["method"], scope["path"]))
            context = current_request.set(phase)
            try:
                await app(scope, receive, send)
            except Exception as error:
                with self.lock:
                    self.errors["unhandled/" + phase.endpoint + "/" + type(error).__name__] += 1
                raise
            finally:
                current_request.reset(context)

        return instrumented

    def instrument_engine(self, engine, monkeypatch):
        pool = engine.pool
        pool_queue = pool._pool  # SQLAlchemy QueuePool internals, only in this benchmark.
        get = pool_queue.get

        def queue_get(block=True, timeout=None):
            if not block:
                return get(block, timeout)
            begin = time.perf_counter()
            with self.lock:
                self.waiting += 1
                self.peaks["queue_waiters"] = max(self.peaks["queue_waiters"], self.waiting)
            try:
                return get(block, timeout)
            finally:
                with self.lock:
                    self.waiting -= 1
                    self.samples["queue_wait"].append(1000 * (time.perf_counter() - begin))

        monkeypatch.setattr(pool_queue, "get", queue_get)

        @event.listens_for(pool, "checkout")
        def checkout(_connection, record, _proxy):
            now = time.perf_counter()
            with self.lock:
                self.held[record] = (now, current_request.get())
                self.peaks["checked_out"] = max(self.peaks["checked_out"], len(self.held))
                self.peaks["overflow"] = max(self.peaks["overflow"], pool.overflow())

        @event.listens_for(pool, "checkin")
        def checkin(_connection, record):
            now = time.perf_counter()
            with self.lock:
                if record in self.held:
                    begin, _ = self.held.pop(record)
                    self.samples["checkout_hold"].append(1000 * (now - begin))

        @event.listens_for(engine, "before_cursor_execute")
        def before_sql(_connection, _cursor, _statement, _parameters, context, _many):
            context._load_started = time.perf_counter()

        @event.listens_for(engine, "after_cursor_execute")
        def after_sql(_connection, _cursor, _statement, _parameters, context, _many):
            ms = 1000 * (time.perf_counter() - context._load_started)
            with self.lock:
                self.samples["sql"].append(ms)

        @event.listens_for(engine, "handle_error")
        def sql_error(context):
            state = getattr(context.original_exception, "sqlstate", None)
            if state:
                with self.lock:
                    self.errors["sqlstate/" + state] += 1

    async def sample_loop(self, limiter):
        while True:
            begin = time.perf_counter()
            await asyncio.sleep(.05)
            lag = max(0, 1000 * (time.perf_counter() - begin - .05))
            stats = limiter.statistics()
            with self.lock:
                serialized = sum(bool(phase and phase.serializing) for _, phase in self.held.values())
                for key, count in (
                    ("thread_tokens_borrowed", stats.borrowed_tokens),
                    ("thread_tasks_waiting", stats.tasks_waiting),
                    ("held_during_serialization", serialized),
                ):
                    self.peaks[key] = max(self.peaks[key], count)
                self.samples["event_loop_lag"].append(lag)
                if (
                    stats.borrowed_tokens == stats.total_tokens and self.waiting
                    and serialized > self.last_saturation.get("held_during_serialization", 0)
                ):
                    self.last_saturation = {
                        "thread_tokens_borrowed": stats.borrowed_tokens,
                        "thread_tasks_waiting": stats.tasks_waiting,
                        "queue_waiters": self.waiting,
                        "checked_out": len(self.held),
                        "held_during_serialization": serialized,
                        "serialized_endpoints": dict(Counter(
                            phase.endpoint for _, phase in self.held.values()
                            if phase and phase.serializing
                        )),
                    }

    def watch_postgres(self, engine):
        def sample():
            with engine.connect().execution_options(isolation_level="AUTOCOMMIT") as connection:
                while not self.stop.is_set():
                    stats = pg_activity(connection)
                    with self.lock:
                        for key, count in stats.items():
                            name = "postgres/" + key
                            self.peaks[name] = max(self.peaks[name], count)
                    self.stop.wait(.2)

        monitor = Thread(target=sample, daemon=True)
        monitor.start()
        return monitor

    def report(self, engine, sql_engine):
        with sql_engine.connect().execution_options(isolation_level="AUTOCOMMIT") as connection:
            activity = pg_activity(connection)
        with self.lock:
            return {
                "measurements": {name: distribution(values) for name, values in self.samples.items()},
                "peaks": dict(self.peaks),
                "thread_tokens": self.thread_tokens,
                "saturation_sample": self.last_saturation.copy(),
                "errors": dict(self.errors),
                "pool_timeouts": sum(
                    count for name, count in self.errors.items()
                    if name.endswith("/" + PoolTimeout.__name__) and name.startswith("http_cause/")
                ),
                "after_wave": {
                    "checked_out": engine.pool.checkedout(), "pool_waiters": self.waiting,
                    "postgres": dict(activity),
                },
            }
