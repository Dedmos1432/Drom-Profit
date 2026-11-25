"""Точка входа импорта и выбор источника.

Источник выбирается один на процесс: ``demo`` или ``drom``. Смешивание
не поддерживается, чтобы демо-данные нельзя было выдать за реальные.
"""

import logging
import os
from collections.abc import Mapping
from pathlib import Path
from typing import Literal, Protocol

from .errors import ListingImportError
from .fixture_provider import FixtureListingProvider
from .models import ListingData
from .urls import HostAllowlist

logger = logging.getLogger(__name__)

SourceMode = Literal["demo", "drom"]
REPO_ROOT = Path(__file__).resolve().parents[4]
DEFAULT_FIXTURE_PATH = REPO_ROOT / "shared" / "fixtures" / "import-listings.json"


class ListingProvider(Protocol):
    source: str

    async def fetch(self, url: str) -> ListingData: ...


class ListingImporter:
    """``async (url: str) -> ListingData``; наружу выходит только ``ListingImportError``."""

    def __init__(self, provider: ListingProvider):
        self.provider = provider

    @property
    def source(self) -> str:
        return self.provider.source

    async def __call__(self, url: str) -> ListingData:
        try:
            listing = await self.provider.fetch(url)
        except ListingImportError as error:
            logger.info("listing import failed: %s (%s)", error.code, error.detail)
            raise
        except (ValueError, TypeError, KeyError, OverflowError, RecursionError) as error:
            logger.warning("listing import parse failure: %s", type(error).__name__)
            raise ListingImportError("parse_error", type(error).__name__) from error
        if listing.source != self.provider.source:
            raise ListingImportError("parse_error", "источник результата не совпадает")
        return listing


def make_importer(
    source: SourceMode = "demo",
    *,
    fixture_path: Path | None = None,
    drom_access_approved: bool = False,
    drom_hosts: HostAllowlist | None = None,
    drom_redirect_hosts: HostAllowlist | None = None,
) -> ListingImporter:
    if source == "demo":
        return ListingImporter(FixtureListingProvider.from_file(fixture_path or DEFAULT_FIXTURE_PATH))
    if source == "drom":
        from .drom_provider import DromHtmlProvider

        return ListingImporter(DromHtmlProvider(
            access_approved=drom_access_approved,
            allowed_hosts=drom_hosts,
            redirect_hosts=drom_redirect_hosts,
        ))
    raise ValueError(f"Неизвестный источник объявлений: {source!r}")


def importer_from_env(environ: Mapping[str, str] = os.environ) -> ListingImporter:
    """Конфигурация из окружения.

    * ``DROM_LISTINGS_SOURCE`` — ``demo`` (по умолчанию) или ``drom``;
    * ``DROM_LISTINGS_FIXTURE`` — путь к JSON для demo;
    * ``DROM_LISTINGS_ACCESS_APPROVED=yes`` — обязателен для ``drom``;
    * ``DROM_LISTINGS_HOSTS`` и ``DROM_LISTINGS_REDIRECT_HOSTS`` — точные хосты через запятую.
    """
    source = environ.get("DROM_LISTINGS_SOURCE", "demo").strip().lower() or "demo"
    fixture = environ.get("DROM_LISTINGS_FIXTURE")
    approved = environ.get("DROM_LISTINGS_ACCESS_APPROVED", "").strip().lower() == "yes"
    if source not in ("demo", "drom"):
        raise ValueError(f"DROM_LISTINGS_SOURCE: ожидается demo или drom, получено {source!r}")
    if source == "demo":
        return make_importer(source, fixture_path=Path(fixture) if fixture else None)

    hosts = environ.get("DROM_LISTINGS_HOSTS", "").strip()
    redirects = environ.get("DROM_LISTINGS_REDIRECT_HOSTS", "").strip()
    return make_importer(
        source,
        drom_access_approved=approved,
        drom_hosts=HostAllowlist(hosts.split(",")) if hosts else None,
        drom_redirect_hosts=HostAllowlist(redirects.split(",")) if redirects else None,
    )


_default: ListingImporter | None = None


async def import_listing(url: str) -> ListingData:
    """Удобная функция с источником из окружения (создается при первом вызове)."""
    global _default
    if _default is None:
        _default = importer_from_env()
    return await _default(url)
