import json
from collections.abc import Awaitable, Callable
from pathlib import Path
from typing import Literal
from urllib.parse import urlsplit

from ..schemas import ListingData


ImportErrorCode = Literal[
    "invalid_url", "listing_unavailable", "source_unavailable", "source_forbidden", "parse_error"
]
ListingImporter = Callable[[str], Awaitable[ListingData]]


class ListingImportError(Exception):
    def __init__(self, code: ImportErrorCode):
        super().__init__(code)
        self.code = code


def make_demo_importer(fixture_path: Path) -> ListingImporter:
    previews = [ListingData.model_validate(row) for row in json.loads(fixture_path.read_text("utf-8"))]
    if any(preview.source != "demo" or preview.listing_url is None for preview in previews):
        raise ValueError("Демонстрационные объявления должны иметь source=demo и URL")
    by_url = {str(preview.listing_url): preview for preview in previews}
    allowed_hosts = {urlsplit(url).hostname for url in by_url}

    async def import_listing(url: str) -> ListingData:
        try:
            parts = urlsplit(url)
            valid = (
                parts.scheme == "https"
                and parts.hostname in allowed_hosts
                and parts.netloc == parts.hostname
                and parts.port is None
                and not any(character.isspace() for character in url)
            )
        except ValueError:
            valid = False
        if not valid:
            raise ListingImportError("invalid_url")
        if url not in by_url:
            raise ListingImportError("listing_unavailable")
        return by_url[url].model_copy()

    return import_listing
