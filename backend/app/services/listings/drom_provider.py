"""Реальный источник Дрома через разрешенный HTML-парсинг.

ВЫКЛЮЧЕН ПО УМОЛЧАНИЮ. Создать провайдер можно только с явным
``access_approved=True`` — это фиксирует, что команда получила согласованное
разрешение на автоматизированный доступ. До этого модуль не обращается к Дрому.
"""

import asyncio
import time
from datetime import datetime, timezone

from .errors import ListingImportError
from .html_parser import extract_listing_fields, looks_blocked
from .http_worker import get_html
from .models import ListingData
from .normalize import normalize_listing
from .safe_http import HttpLimits, HttpResponse, SafeHttpClient
from .urls import HostAllowlist, ListingUrl, validate_listing_url

# Верхняя граница предыдущей политики. Для запуска требуются точные хосты внутри неё.
_DROM_HOST_SCOPE = HostAllowlist(("*.drom.ru",))


class DromHtmlProvider:
    source = "drom"

    def __init__(
        self,
        *,
        access_approved: bool,
        allowed_hosts: HostAllowlist | None = None,
        redirect_hosts: HostAllowlist | None = None,
        limits: HttpLimits = HttpLimits(),
        client: SafeHttpClient | None = None,
        max_concurrency: int = 4,
    ):
        if access_approved is not True:
            raise RuntimeError(
                "Доступ к Дрому не согласован: используйте демонстрационный источник"
            )
        if allowed_hosts is None:
            raise RuntimeError("Не настроен согласованный список точных хостов Дрома")
        redirects = redirect_hosts.entries if redirect_hosts else ()
        if any(host not in _DROM_HOST_SCOPE for host in (*allowed_hosts.entries, *redirects)):
            raise ValueError("Разрешены только точные имена поддоменов Дрома")
        if max_concurrency < 1:
            raise ValueError("Лимит параллельных запросов должен быть положительным")
        self.allowed_hosts = allowed_hosts
        self._redirect_hosts = redirects
        self._limits = limits
        self._client = client
        self._semaphore = asyncio.Semaphore(max_concurrency)
        self._requests: set[asyncio.Task[HttpResponse]] = set()

    def _request_done(self, task: asyncio.Task[HttpResponse]) -> None:
        self._requests.discard(task)
        if not task.cancelled():
            task.exception()  # При отключении клиента результат фонового запроса некому принять.

    async def _fetch_html(self, target: ListingUrl, deadline: float) -> HttpResponse:
        try:
            if self._client is not None:
                return await asyncio.to_thread(self._client.get_html, target.canonical)
            return await asyncio.to_thread(
                get_html, target, self.allowed_hosts, self._redirect_hosts, self._limits, deadline
            )
        finally:
            self._semaphore.release()

    async def fetch(self, url: str) -> ListingData:
        target = validate_listing_url(url, self.allowed_hosts)
        if target.path.startswith("/demo/"):
            raise ListingImportError("listing_unavailable", "ссылка демонстрационного источника")
        deadline = time.monotonic() + self._limits.total_timeout
        try:
            await asyncio.wait_for(self._semaphore.acquire(), timeout=self._limits.total_timeout)
        except TimeoutError:
            raise ListingImportError("source_unavailable", "таймаут ожидания сетевого запроса") from None
        task = asyncio.create_task(self._fetch_html(target, deadline))
        self._requests.add(task)
        task.add_done_callback(self._request_done)
        response = await asyncio.shield(task)
        if not target.matches_listing(response.url):
            raise ListingImportError("parse_error", "конечная ссылка относится к другому объявлению")
        try:
            html = response.body.decode(response.charset, errors="replace")
        except LookupError:
            html = response.body.decode("utf-8", errors="replace")
        return listing_from_html(html, response.url)


def listing_from_html(html: str, target: ListingUrl) -> ListingData:
    raw = extract_listing_fields(html, target=target)
    listing = normalize_listing(
        raw,
        source="drom",
        listing_url=target.canonical,
        listing_id=target.listing_id,
        fetched_at=datetime.now(timezone.utc).isoformat(timespec="seconds"),
    )
    if listing.price is None:
        if looks_blocked(html):
            raise ListingImportError("source_forbidden", "страница проверки или блокировки")
        if not listing.brand or not listing.model:
            raise ListingImportError("parse_error", "объявление без цены не распознано однозначно")
    return listing
