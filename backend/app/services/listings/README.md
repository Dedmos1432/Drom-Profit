# Импорт объявлений (`app.services.listings`)

Зона Data Integration (разработчик №4). Модуль принимает ссылку на объявление и
возвращает структуру `ListingData` из `shared/openapi.yaml`. Он не обращается к БД,
не создает сессии, не сохраняет данные и не рассчитывает рыночную цену.

Зависимости — только стандартная библиотека Python 3.13+; в `requirements.txt` ничего
не добавляется.

## Интерфейс

```python
from app.services.listings import ListingData, ListingImportError, importer_from_env

importer = importer_from_env()        # ListingImporter: async (url: str) -> ListingData
listing = await importer(url)         # бросает только ListingImportError
listing.as_dict()                     # dict из 18 полей контракта, порядок как в схеме
importer.source                       # "demo" или "drom"
```

| Объект | Назначение |
|---|---|
| `ListingData` | неизменяемый dataclass, проверяет типы и ограничения контракта; `as_dict()` / `from_dict()` |
| `ListingImporter` | вызываемый объект `async (url) -> ListingData`; посторонние ошибки разбора превращает в `parse_error` |
| `ListingImportError(code, detail)` | `code` — машинный код; `detail` — текст для логов, не для пользователя |
| `make_importer(source, fixture_path=None, drom_access_approved=False)` | явное создание |
| `importer_from_env(environ)` | создание по переменным окружения (ниже) |
| `import_listing(url)` | функция-обертка над `importer_from_env()` для простых вызовов |

### Ошибки

| `code` | HTTP (переводит Core) | Когда |
|---|---|---|
| `invalid_url` | 400 | не https, чужой хост, порт, учетные данные, IP, не путь `*.html`, управляющие символы, > 2048 символов |
| `listing_unavailable` | 404 | нет в demo-наборе; HTTP 404/410; снято с продажи; перенаправление на не-объявление того же сайта |
| `source_forbidden` | 403 | HTTP 401/403/407/429/451; страница CAPTCHA или блокировки |
| `parse_error` | 502 | не HTML, сжатый или слишком большой ответ, нет структурированных данных, нет марки, модели и цены |
| `source_unavailable` | 503 | DNS/сеть/TLS/таймаут, HTTP 5xx, непубличный адрес, перенаправление на чужой хост, лимит перенаправлений |

## Конфигурация

| Переменная | По умолчанию | Значение |
|---|---|---|
| `DROM_LISTINGS_SOURCE` | `demo` | `demo` или `drom`; смешивания нет |
| `DROM_LISTINGS_FIXTURE` | `<repo>/shared/fixtures/import-listings.json` | путь к demo-набору |
| `DROM_LISTINGS_ACCESS_APPROVED` | пусто | `yes` обязателен для `drom`, иначе приложение не стартует |

## Подключение к Core Backend (предложение)

Сейчас в `feature/backend-core` есть stub `app/services/listing_import.py`. Чтобы
подключить модуль без изменения API и схем, достаточно заменить stub адаптером:

```python
# app/services/listing_import.py
from collections.abc import Awaitable, Callable

from ..schemas import ListingData
from .listings import ImportErrorCode, ListingImportError, importer_from_env  # noqa: F401

ListingImporter = Callable[[str], Awaitable[ListingData]]


def make_listings_importer() -> ListingImporter:
    importer = importer_from_env()

    async def import_listing(url: str) -> ListingData:
        return ListingData.model_validate((await importer(url)).as_dict())

    return import_listing
```

и в `app/main.py` использовать `make_listings_importer()` вместо `make_demo_importer(...)`.
`ListingImportError` — тот же класс с тем же атрибутом `code`, поэтому `IMPORT_ERRORS`
в `api/cars.py` не меняется. Проверено локально: результаты demo и синтетической
страницы проходят `schemas.ListingData.model_validate`.

## Demo-режим

Источник — `shared/fixtures/import-listings.json`. Каждая запись обязана иметь
`source = "demo"` и уникальный `listing_url`. Разрешены только хосты из самих fixtures
(`auto.drom.ru`), query и fragment игнорируются. Остальные ссылки Дрома в demo-режиме
дают `invalid_url` (другой хост) или `listing_unavailable` (неизвестный путь). Сеть
не используется.

## Нормализация

* Целые рубли: `"1 200 000 ₽"` → `1200000`; 0, отрицательные, «от …», «договорная», не-RUB → `null`.
* Пробег в км: `"145 тыс. км"` → `145000`; «без пробега по РФ» и мили → `null`.
* Объем в литрах с одним знаком: `"2494 см³"` → `2.5`. Мощность в л.с.: `"133 кВт"` → `181`.
* Год 1886…(текущий + 1).
* Словари (формат fixtures, **предложение к согласованию с Web/Android**):
  топливо `Бензин | Дизель | Гибрид | Электро | Газ`, коробка `Механика | Автомат | Робот | Вариатор`,
  привод `Передний | Задний | Полный`. Значение вне словаря сохраняется очищенным текстом
  (например, «Подключаемый полный»), чтобы не терять сведения.
* `image_url` — только `https://` без учетных данных. Сервер фотографию не загружает.
* `market_valuation` — только опубликованная источником оценка; сейчас всегда `null`.

## Реальный источник (выключен)

`DromHtmlProvider` + `SafeHttpClient` готовы, но создаются только при
`DROM_LISTINGS_ACCESS_APPROVED=yes`. Без согласованного разрешения запросы к Дрому не выполняются.

* Allowlist по умолчанию: `auto.drom.ru`, `*.drom.ru` (ровно одна метка). Требует согласования.
* DNS разрешается один раз, все адреса должны быть публичными; соединение идет на проверенный IP,
  TLS проверяется по имени хоста; адрес пира перепроверяется. Прокси из окружения не используются.
* Перенаправления вручную, до 3, каждая цель проходит ту же проверку URL.
* Таймауты: connect 5 с, чтение 10 с, общий 15 с; размер ответа ≤ 2 МБ; `Accept-Encoding: identity`.
* Не более 4 одновременных запросов на процесс. Cookies и авторизация не передаются, CAPTCHA не обходится.
* Парсер читает schema.org JSON-LD (`Car`/`Vehicle`/`Product` + `Offer`) и `og:image`. **Соответствие
  реальной разметке Дрома не проверено** — тесты используют синтетическую страницу
  `backend/tests/listings/samples/`. После согласования нужны разрешенные образцы страниц, чтобы
  сверить поля и добавить рыночную оценку.

## Тесты

Из каталога `backend/`:

```
python -m pytest -q tests/listings
```

Тесты не открывают сокеты: сетевой клиент проверяется на подставных resolver и transport.

## Исследование отчетов

См. [REPORTS-RESEARCH.md](REPORTS-RESEARCH.md). Обработка отчетов не реализована (вне P0).
