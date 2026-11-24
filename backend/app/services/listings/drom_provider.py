"""Реальный источник Дрома через разрешенный HTML-парсинг.

ВЫКЛЮЧЕН ПО УМОЛЧАНИЮ. Создать провайдер можно только с явным
``access_approved=True`` — это фиксирует, что команда получила согласованное
разрешение на автоматизированный доступ. До этого модуль не обращается к Дрому.
"""

import asyncio
from datetime import datetime, timezone

from .errors import ListingImportError
from .html_parser import extract_listing_fields
from .models import ListingData
from .normalize import normalize_listing
from .safe_http import HttpLimits, SafeHttpClient
from .urls import HostAllowlist, validate_listing_url

# Предложение для согласования: карточки объявлений на городских поддоменах и auto.drom.ru.
DEFAULT_DROM_HOSTS = ("auto.drom.ru", "*.drom.ru")


class DromHtmlProvider:
    source = "drom"

    def __init__(
        self,
        *,
        access_approved: bool,
        allowed_hosts: HostAllowlist | None = None,
        limits: HttpLimits = HttpLimits(),
        client: SafeHttpClient | None = None,
        max_concurrency: int = 4,
    ):
        if access_approved is not True:
            raise RuntimeError(
                "Доступ к Дрому не согласован: используйте демонстрационный источник"
            )
        self.allowed_hosts = allowed_hosts or HostAllowlist(DEFAULT_DROM_HOSTS)
        self._client = client or SafeHttpClient(self.allowed_hosts, limits)
        self._semaphore = asyncio.Semaphore(max_concurrency)

    async def fetch(self, url: str) -> ListingData:
        validate_listing_url(url, self.allowed_hosts)  # быстрый отказ до потока и сети
        async with self._semaphore:
            response = await asyncio.to_thread(self._client.get_html, url)
        try:
            html = response.body.decode(response.charset, errors="replace")
        except LookupError:
            html = response.body.decode("utf-8", errors="replace")
        raw = extract_listing_fields(html)
        listing = normalize_listing(
            raw,
            source="drom",
            listing_url=response.url.canonical,
            listing_id=response.url.listing_id,
            fetched_at=datetime.now(timezone.utc).isoformat(timespec="seconds"),
        )
        if listing.brand is None and listing.model is None and listing.price is None:
            raise ListingImportError("parse_error", "не найдены марка, модель и цена")
        return listing
