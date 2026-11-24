"""Сетевой клиент проверяется на подставных resolver/transport — без сокетов."""

import pytest

from app.services.listings.errors import ListingImportError
from app.services.listings.safe_http import (
    HttpLimits,
    RawResponse,
    SafeHttpClient,
    is_public_address,
)
from app.services.listings.urls import HostAllowlist

URL = "https://vladivostok.drom.ru/toyota/camry/12345678.html"
PUBLIC_IP = "93.184.216.34"
HTML = {"content-type": "text/html; charset=utf-8"}


class FakeTransport:
    def __init__(self, *responses):
        self.responses = list(responses)
        self.calls = []

    def __call__(self, address, host, path, headers, limits, deadline):
        self.calls.append((address, host, path, headers))
        item = self.responses.pop(0)
        if isinstance(item, BaseException):
            raise item
        status, headers_, chunks = item
        return RawResponse(status=status, headers=headers_, chunks=iter(chunks))


def client(transport, resolver=lambda host: [PUBLIC_IP], **limits):
    return SafeHttpClient(
        HostAllowlist(["*.drom.ru"]), HttpLimits(**limits), resolver=resolver, transport=transport
    )


def code_of(call):
    with pytest.raises(ListingImportError) as error:
        call()
    return error.value.code


def test_success_pins_resolved_ip_and_sends_safe_headers():
    transport = FakeTransport((200, HTML, [b"<html>", b"ok</html>"]))
    response = client(transport).get_html(URL + "?x=1")
    assert response.body == b"<html>ok</html>"
    assert response.charset == "utf-8"
    address, host, path, headers = transport.calls[0]
    assert (address, host, path) == (PUBLIC_IP, "vladivostok.drom.ru", "/toyota/camry/12345678.html")
    assert headers["Accept-Encoding"] == "identity"
    assert "Cookie" not in headers and "Authorization" not in headers


@pytest.mark.parametrize(
    "addresses",
    [["127.0.0.1"], ["10.0.0.5"], ["192.168.1.1"], ["169.254.169.254"], ["::1"], ["fc00::1"],
     ["::ffff:127.0.0.1"], ["0.0.0.0"], [PUBLIC_IP, "10.0.0.1"], []],
)
def test_rejects_private_dns_answers(addresses):
    transport = FakeTransport()
    assert code_of(lambda: client(transport, lambda host: addresses).get_html(URL)) == (
        "source_unavailable"
    )
    assert transport.calls == []


def test_dns_failure_is_source_unavailable():
    def fail(host):
        raise OSError("nxdomain")

    assert code_of(lambda: client(FakeTransport(), fail).get_html(URL)) == "source_unavailable"


def test_follows_allowed_listing_redirect():
    target = "https://moscow.drom.ru/toyota/camry/12345678.html"
    transport = FakeTransport((301, {"location": target}, []), (200, HTML, [b"ok"]))
    response = client(transport).get_html(URL)
    assert response.url.canonical == target
    assert len(transport.calls) == 2


@pytest.mark.parametrize(
    ("location", "code"),
    [
        ("https://evil.example/1.html", "source_unavailable"),
        ("http://moscow.drom.ru/toyota/camry/1.html", "source_unavailable"),
        ("https://127.0.0.1/1.html", "source_unavailable"),
        ("//evil.example/1.html", "source_unavailable"),
        ("/toyota/camry/", "listing_unavailable"),
        ("https://moscow.drom.ru/auto/all/", "listing_unavailable"),
        (None, "parse_error"),
    ],
)
def test_rejects_unexpected_redirects(location, code):
    headers = {} if location is None else {"location": location}
    transport = FakeTransport((302, headers, []))
    assert code_of(lambda: client(transport).get_html(URL)) == code
    assert len(transport.calls) == 1


def test_redirect_loop_is_limited():
    loop = (302, {"location": URL}, [])
    transport = FakeTransport(*[loop] * 5)
    assert code_of(lambda: client(transport, max_redirects=2).get_html(URL)) == "source_unavailable"
    assert len(transport.calls) == 3


@pytest.mark.parametrize(
    ("status", "code"),
    [(404, "listing_unavailable"), (410, "listing_unavailable"), (403, "source_forbidden"),
     (429, "source_forbidden"), (401, "source_forbidden"), (500, "source_unavailable"),
     (503, "source_unavailable"), (204, "parse_error"), (400, "parse_error")],
)
def test_status_mapping(status, code):
    transport = FakeTransport((status, HTML, [b""]))
    assert code_of(lambda: client(transport).get_html(URL)) == code


def test_size_limit_by_header_and_by_stream():
    declared = FakeTransport((200, HTML | {"content-length": "5000"}, [b"x"]))
    assert code_of(lambda: client(declared, max_bytes=1000).get_html(URL)) == "parse_error"
    streamed = FakeTransport((200, HTML, [b"x" * 600, b"x" * 600]))
    assert code_of(lambda: client(streamed, max_bytes=1000).get_html(URL)) == "parse_error"


def test_rejects_non_html_and_compressed():
    json_body = FakeTransport((200, {"content-type": "application/json"}, [b"{}"]))
    assert code_of(lambda: client(json_body).get_html(URL)) == "parse_error"
    gzip_body = FakeTransport((200, HTML | {"content-encoding": "gzip"}, [b"x"]))
    assert code_of(lambda: client(gzip_body).get_html(URL)) == "parse_error"


@pytest.mark.parametrize("error", [TimeoutError(), ConnectionResetError(), OSError("tls")])
def test_network_errors_are_source_unavailable(error):
    assert code_of(lambda: client(FakeTransport(error)).get_html(URL)) == "source_unavailable"


def test_total_deadline():
    ticks = iter([0.0, 0.0, 100.0, 100.0])
    transport = FakeTransport((200, HTML, [b"a", b"b"]))
    http = SafeHttpClient(
        HostAllowlist(["*.drom.ru"]), HttpLimits(total_timeout=10),
        resolver=lambda host: [PUBLIC_IP], transport=transport, clock=lambda: next(ticks),
    )
    assert code_of(lambda: http.get_html(URL)) == "source_unavailable"


def test_invalid_url_never_reaches_network():
    transport = FakeTransport()
    assert code_of(lambda: client(transport).get_html("https://evil.example/1.html")) == (
        "invalid_url"
    )
    assert transport.calls == []


@pytest.mark.parametrize(
    ("address", "public"),
    [(PUBLIC_IP, True), ("2606:4700::1111", True), ("100.64.0.1", False), ("224.0.0.1", False),
     ("fe80::1%eth0", False), ("garbage", False)],
)
def test_is_public_address(address, public):
    assert is_public_address(address) is public
