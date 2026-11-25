"""Демонстрационный источник: объявления из ``shared/fixtures/import-listings.json``.

URL вида ``https://auto.drom.ru/demo/1.html`` — синтетические ключи, сеть не используется.
Все записи обязаны иметь ``source = "demo"``, поэтому демо-данные нельзя выдать за реальные.
"""

import json
from pathlib import Path

from .errors import ListingImportError
from .models import ListingData
from .urls import HostAllowlist, validate_listing_url


class FixtureListingProvider:
    source = "demo"

    def __init__(self, listings: list[ListingData]):
        if not listings:
            raise ValueError("Демонстрационный набор объявлений пуст")
        hosts: set[str] = set()
        for listing in listings:
            if listing.source != "demo" or listing.listing_url is None:
                raise ValueError("Демонстрационные объявления должны иметь source=demo и URL")
            hosts.add(listing.listing_url.split("/")[2])
        self.allowed_hosts = HostAllowlist(hosts)
        self._by_url: dict[str, ListingData] = {}
        for listing in listings:
            key = validate_listing_url(listing.listing_url, self.allowed_hosts).canonical
            if key in self._by_url:
                raise ValueError(f"Повторяющийся URL в демонстрационных данных: {key}")
            self._by_url[key] = listing

    @classmethod
    def from_file(cls, path: Path) -> "FixtureListingProvider":
        rows = json.loads(Path(path).read_text("utf-8"))
        if not isinstance(rows, list):
            raise ValueError("Ожидается JSON-массив ListingData")
        return cls([ListingData.from_dict(row) for row in rows])

    async def fetch(self, url: str) -> ListingData:
        canonical = validate_listing_url(url, self.allowed_hosts).canonical
        listing = self._by_url.get(canonical)
        if listing is None:
            raise ListingImportError("listing_unavailable", "нет в демонстрационном наборе")
        return listing  # dataclass неизменяемый, копия не нужна
