"""Безопасный HTTP GET для страниц объявлений (только стандартная библиотека).

Защита от SSRF и злоупотреблений:
* каждый URL, включая цели перенаправлений, проходит ``validate_listing_url``
  (https, точный allowlist хостов, без учетных данных и портов);
* DNS разрешается один раз; все адреса обязаны быть публичными (``is_global``),
  соединение устанавливается именно с проверенным IP (нет повторного разрешения
  и DNS rebinding), адрес пира перепроверяется после connect;
* TLS с проверкой сертификата по исходному имени хоста (SNI);
* переменные окружения прокси не используются;
* перенаправления обрабатываются вручную, их число ограничено;
* ограничены таймауты (connect, чтение, общий срок) и размер ответа,
  сжатие не запрашивается (нет «zip-бомб»).

Модуль не обходит CAPTCHA, авторизацию и антибот-защиту: такие ответы
превращаются в ``source_forbidden``.
"""

import http.client
import ipaddress
import socket
import ssl
import time
from collections.abc import Callable, Iterable, Iterator
from dataclasses import dataclass, field

from .errors import ListingImportError
from .urls import HostAllowlist, ListingUrl, validate_listing_url

USER_AGENT = "DromProfit-ListingImport/0.1 (+internship project; preview only)"
_REDIRECTS = {301, 302, 303, 307, 308}
_GONE = {404, 410}
_FORBIDDEN = {401, 403, 407, 429, 451}
_HTML_TYPES = ("text/html", "application/xhtml+xml")


@dataclass(frozen=True, slots=True)
class HttpLimits:
    connect_timeout: float = 5.0
    read_timeout: float = 10.0
    total_timeout: float = 15.0
    max_bytes: int = 2_000_000
    max_redirects: int = 3


@dataclass(frozen=True, slots=True)
class HttpResponse:
    url: ListingUrl
    status: int
    headers: dict[str, str] = field(repr=False)
    body: bytes = field(repr=False)

    @property
    def charset(self) -> str:
        for part in self.headers.get("content-type", "").split(";")[1:]:
            key, _, value = part.strip().partition("=")
            if key.lower() == "charset" and value:
                return value.strip("\"'")
        return "utf-8"


@dataclass(frozen=True, slots=True)
class RawResponse:
    status: int
    headers: dict[str, str]  # ключи в нижнем регистре
    chunks: Iterator[bytes]
    close: Callable[[], None] = lambda: None


Resolver = Callable[[str], list[str]]
Transport = Callable[[str, str, str, dict[str, str], HttpLimits, float], RawResponse]


def system_resolver(host: str) -> list[str]:
    infos = socket.getaddrinfo(host, 443, type=socket.SOCK_STREAM, proto=socket.IPPROTO_TCP)
    return list(dict.fromkeys(info[4][0] for info in infos))


def is_public_address(address: str) -> bool:
    try:
        ip = ipaddress.ip_address(address.split("%", 1)[0])
    except ValueError:
        return False
    if isinstance(ip, ipaddress.IPv6Address) and ip.ipv4_mapped is not None:
        ip = ip.ipv4_mapped
    return ip.is_global and not (ip.is_multicast or ip.is_reserved or ip.is_link_local)


class _PinnedHTTPSConnection(http.client.HTTPSConnection):
    """HTTPS-соединение с заранее проверенным IP и проверкой сертификата по имени хоста."""

    def __init__(self, host: str, address: str, timeout: float, context: ssl.SSLContext):
        super().__init__(host, 443, timeout=timeout, context=context)
        self._address = address

    def connect(self) -> None:
        sock = socket.create_connection((self._address, 443), self.timeout)
        try:
            peer = sock.getpeername()[0]
            if (
                not is_public_address(peer)
                or ipaddress.ip_address(peer) != ipaddress.ip_address(self._address)
            ):
                raise ListingImportError("source_unavailable", "адрес пира не совпадает с проверенным IP")
            self.sock = self._context.wrap_socket(sock, server_hostname=self.host)
        except BaseException:
            sock.close()
            raise


def socket_transport(
    address: str, host: str, path: str, headers: dict[str, str], limits: HttpLimits,
    deadline: float,
) -> RawResponse:
    context = ssl.create_default_context()
    timeout = max(0.1, min(limits.connect_timeout, deadline - time.monotonic()))
    connection = _PinnedHTTPSConnection(host, address, timeout, context)
    try:
        connection.request("GET", path, headers=headers)
        if connection.sock is not None:
            connection.sock.settimeout(max(0.1, min(limits.read_timeout, deadline - time.monotonic())))
        response = connection.getresponse()
    except BaseException:
        connection.close()
        raise

    def chunks() -> Iterator[bytes]:
        while chunk := response.read(64 * 1024):
            yield chunk

    def close() -> None:
        try:
            response.close()
        finally:
            connection.close()

    return RawResponse(
        status=response.status,
        headers={key.lower(): value for key, value in response.getheaders()},
        chunks=chunks(),
        close=close,
    )


class SafeHttpClient:
    def __init__(
        self,
        allowed_hosts: HostAllowlist,
        limits: HttpLimits = HttpLimits(),
        *,
        resolver: Resolver = system_resolver,
        transport: Transport = socket_transport,
        user_agent: str = USER_AGENT,
        clock: Callable[[], float] = time.monotonic,
        redirect_hosts: tuple[str, ...] | None = None,
    ):
        self.allowed_hosts = allowed_hosts
        self._redirect_hosts = redirect_hosts
        self.limits = limits
        self._resolver = resolver
        self._transport = transport
        self._user_agent = user_agent
        self._clock = clock

    def get_html(self, url: str) -> HttpResponse:
        """Загружает HTML-страницу объявления или бросает ``ListingImportError``."""
        requested = validate_listing_url(url, self.allowed_hosts)
        target = requested
        deadline = self._clock() + self.limits.total_timeout
        for _hop in range(self.limits.max_redirects + 1):
            status, headers, body = self._request(target, deadline)
            if status in _REDIRECTS:
                target = self._redirect_target(target, headers.get("location"))
                if not requested.matches_listing(target):
                    raise ListingImportError("parse_error", "перенаправление сменило объявление")
                continue
            if status in _GONE:
                raise ListingImportError("listing_unavailable", f"HTTP {status}")
            if status in _FORBIDDEN:
                raise ListingImportError("source_forbidden", f"HTTP {status}")
            if status >= 500:
                raise ListingImportError("source_unavailable", f"HTTP {status}")
            if status != 200:
                raise ListingImportError("parse_error", f"неожиданный HTTP {status}")
            return HttpResponse(url=target, status=status, headers=headers, body=body)
        raise ListingImportError("source_unavailable", "слишком много перенаправлений")

    def _redirect_target(self, current: ListingUrl, location: str | None) -> ListingUrl:
        if not location:
            raise ListingImportError("parse_error", "перенаправление без Location")
        if location.startswith("/") and not location.startswith("//"):
            location = f"https://{current.host}{location}"
        allowed = self.allowed_hosts if self._redirect_hosts is None else HostAllowlist(
            (current.host, *self._redirect_hosts)
        )
        try:
            return validate_listing_url(location, allowed)
        except ListingImportError:
            # Удаленные объявления обычно уводят на поиск того же сайта.
            try:
                same_site = location.startswith("https://") and (
                    location[len("https://"):].split("/", 1)[0].lower() in allowed
                )
            except Exception:
                same_site = False
            code = "listing_unavailable" if same_site else "source_unavailable"
            raise ListingImportError(code, "перенаправление вне страницы объявления") from None

    def _request(self, target: ListingUrl, deadline: float) -> tuple[int, dict[str, str], bytes]:
        if self._clock() >= deadline:
            raise ListingImportError("source_unavailable", "общий таймаут")
        address = self._resolve(target.host)
        headers = {
            "Host": target.host,
            "User-Agent": self._user_agent,
            "Accept": "text/html,application/xhtml+xml;q=0.9",
            "Accept-Encoding": "identity",
            "Accept-Language": "ru",
            "Connection": "close",
        }
        try:
            raw = self._transport(address, target.host, target.path, headers, self.limits, deadline)
        except ListingImportError:
            raise
        except (TimeoutError, socket.timeout):
            raise ListingImportError("source_unavailable", "таймаут") from None
        except (OSError, http.client.HTTPException) as error:
            raise ListingImportError("source_unavailable", type(error).__name__) from None
        try:
            if raw.status != 200:
                return raw.status, raw.headers, b""
            media_type = raw.headers.get("content-type", "").split(";")[0].strip().lower()
            if media_type not in _HTML_TYPES:
                raise ListingImportError("parse_error", "ответ источника не HTML")
            if raw.headers.get("content-encoding", "identity").lower() not in ("", "identity"):
                raise ListingImportError("parse_error", "неожиданное сжатие ответа")
            return raw.status, raw.headers, self._read_body(raw, deadline)
        finally:
            raw.close()

    def _resolve(self, host: str) -> str:
        try:
            addresses = self._resolver(host)
        except (OSError, UnicodeError):
            raise ListingImportError("source_unavailable", "DNS не разрешился") from None
        if not addresses:
            raise ListingImportError("source_unavailable", "DNS не вернул адресов")
        if not all(is_public_address(address) for address in addresses):
            raise ListingImportError("source_unavailable", "DNS указывает на непубличный адрес")
        return addresses[0]

    def _read_body(self, raw: RawResponse, deadline: float) -> bytes:
        limit = self.limits.max_bytes
        declared = raw.headers.get("content-length")
        if declared is not None:
            length = declared.strip()
            if (
                not length.isascii() or not length.isdecimal()
                or len(length.lstrip("0")) > len(str(limit))
                or int(length.lstrip("0") or "0") > limit
            ):
                raise ListingImportError("parse_error", "ответ превышает допустимый размер")
        body = bytearray()
        try:
            for chunk in _guarded(raw.chunks):
                body += chunk
                if len(body) > limit:
                    raise ListingImportError("parse_error", "ответ превышает допустимый размер")
                if self._clock() > deadline:
                    raise ListingImportError("source_unavailable", "общий таймаут")
        except ListingImportError:
            raise
        except (TimeoutError, socket.timeout):
            raise ListingImportError("source_unavailable", "таймаут чтения") from None
        except (OSError, http.client.HTTPException) as error:
            raise ListingImportError("source_unavailable", type(error).__name__) from None
        return bytes(body)


def _guarded(chunks: Iterable[bytes]) -> Iterator[bytes]:
    for chunk in chunks:
        if not isinstance(chunk, (bytes, bytearray)):
            raise ListingImportError("parse_error", "некорректное тело ответа")
        yield chunk
