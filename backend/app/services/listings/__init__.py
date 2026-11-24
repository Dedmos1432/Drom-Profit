"""Импорт и нормализация объявлений по ссылке (зона Data Integration).

Публичный интерфейс для Core Backend:

    from app.services.listings import ListingImportError, importer_from_env

    importer = importer_from_env()        # demo по умолчанию
    listing = await importer(url)         # -> listings.ListingData
    payload = listing.as_dict()           # 18 полей ListingData контракта

Модуль не обращается к БД, не создает сессии и не сохраняет данные.
"""

from .errors import IMPORT_ERROR_CODES, ImportErrorCode, ListingImportError
from .importer import (
    DEFAULT_FIXTURE_PATH,
    ListingImporter,
    ListingProvider,
    import_listing,
    importer_from_env,
    make_importer,
)
from .models import ListingData

__all__ = [
    "DEFAULT_FIXTURE_PATH",
    "IMPORT_ERROR_CODES",
    "ImportErrorCode",
    "ListingData",
    "ListingImportError",
    "ListingImporter",
    "ListingProvider",
    "import_listing",
    "importer_from_env",
    "make_importer",
]
