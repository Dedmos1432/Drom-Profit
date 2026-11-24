"""Ошибки импорта объявлений.

Коды совпадают с описанием ``shared/openapi.yaml`` и со stub Core Backend
(``app/services/listing_import.py``). Перевод в HTTP выполняет Core Backend:

* ``invalid_url`` — 400, ссылка не прошла проверку;
* ``listing_unavailable`` — 404, объявление удалено, снято или не найдено;
* ``source_forbidden`` — 403, источник запретил доступ (в т. ч. CAPTCHA, авторизация);
* ``parse_error`` — 502, ответ источника не удалось разобрать;
* ``source_unavailable`` — 503, источник недоступен, таймаут, сетевой сбой.
"""

from typing import Literal, get_args

ImportErrorCode = Literal[
    "invalid_url", "listing_unavailable", "source_unavailable", "source_forbidden", "parse_error"
]
IMPORT_ERROR_CODES: frozenset[str] = frozenset(get_args(ImportErrorCode))


class ListingImportError(Exception):
    """Единственное исключение, которое модуль выпускает наружу.

    ``code`` — машинный код для API. ``detail`` — технический текст для логов,
    не для пользователя и без персональных данных (URL целиком не включаем).
    """

    def __init__(self, code: ImportErrorCode, detail: str | None = None):
        if code not in IMPORT_ERROR_CODES:
            raise ValueError(f"Неизвестный код ошибки импорта: {code}")
        super().__init__(code if detail is None else f"{code}: {detail}")
        self.code: ImportErrorCode = code
        self.detail = detail
