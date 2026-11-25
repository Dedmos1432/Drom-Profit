"""Проверки границы процесса, очереди и таймаута без DNS и сокетов."""

import asyncio
import json
import threading
import time

import pytest

from app.services.listings import http_worker
from app.services.listings.drom_provider import DromHtmlProvider
from app.services.listings.errors import ListingImportError
from app.services.listings.safe_http import HttpLimits
from app.services.listings.urls import HostAllowlist, validate_listing_url

URL = "https://auto.drom.ru/toyota/camry/12345678.html"
HOSTS = HostAllowlist(("auto.drom.ru",))
TARGET = validate_listing_url(URL, HOSTS)


def test_worker_terminates_while_dns_is_blocked(monkeypatch):
    bootstrap = http_worker._WORKER_CODE.removesuffix("main()")
    monkeypatch.setattr(http_worker, "_WORKER_CODE", (
        bootstrap + "import socket, time; socket.getaddrinfo = lambda *a, **k: time.sleep(10); main()"
    ))
    start = time.monotonic()
    with pytest.raises(ListingImportError) as error:
        http_worker.get_html(TARGET, HOSTS, (), HttpLimits(), start + 0.7)
    assert error.value.code == "source_unavailable"
    assert error.value.detail == "общий таймаут"
    assert time.monotonic() - start < 3


def test_worker_maps_dns_failure_from_its_real_entry_point(monkeypatch):
    bootstrap = http_worker._WORKER_CODE.removesuffix("main()")
    monkeypatch.setattr(http_worker, "_WORKER_CODE", bootstrap + (
        "import socket; "
        "exec('def dns_failure(*args, **kwargs):\\n    raise OSError()'); "
        "socket.getaddrinfo = dns_failure; main()"
    ))
    with pytest.raises(ListingImportError) as error:
        http_worker.get_html(TARGET, HOSTS, (), HttpLimits(), time.monotonic() + 4)
    assert error.value.code == "source_unavailable"
    assert error.value.detail == "DNS не разрешился"


@pytest.mark.parametrize(("response", "expected"), [
    ({"status": 200, "headers": {"content-type": "text/html"}, "url": URL}, b"abc"),
    ({"code": "source_unavailable", "detail": "DNS"}, "source_unavailable"),
])
def test_worker_protocol_preserves_binary_body_and_error_code(monkeypatch, response, expected):
    output = json.dumps(response).encode() + bytes((10,))
    if isinstance(expected, bytes):
        output += expected
    monkeypatch.setattr(http_worker, "_WORKER_CODE", f"import sys; sys.stdout.buffer.write({output!r})")
    deadline = time.monotonic() + 4
    if isinstance(expected, bytes):
        result = http_worker.get_html(TARGET, HOSTS, (), HttpLimits(), deadline)
        assert result.url.canonical == URL
        assert result.body == expected
    else:
        with pytest.raises(ListingImportError) as error:
            http_worker.get_html(TARGET, HOSTS, (), HttpLimits(), deadline)
        assert error.value.code == expected


def test_cancelled_request_keeps_slot_until_network_worker_returns(monkeypatch):
    started = threading.Event()
    release = threading.Event()
    calls = []

    def wait_for_release(*args):
        calls.append(True)
        started.set()
        assert release.wait(5)
        raise ListingImportError("source_unavailable")

    monkeypatch.setattr("app.services.listings.drom_provider.get_html", wait_for_release)

    async def exercise():
        provider = DromHtmlProvider(
            access_approved=True, allowed_hosts=HOSTS, max_concurrency=1,
            limits=HttpLimits(total_timeout=0.2),
        )
        request = asyncio.create_task(provider.fetch(URL))
        try:
            assert await asyncio.to_thread(started.wait, 3)
            request.cancel()
            with pytest.raises(asyncio.CancelledError):
                await request
            with pytest.raises(ListingImportError) as error:
                await provider.fetch(URL)
            assert error.value.code == "source_unavailable"
            assert len(calls) == 1
        finally:
            release.set()
            await asyncio.gather(*provider._requests, return_exceptions=True)

    asyncio.run(exercise())
