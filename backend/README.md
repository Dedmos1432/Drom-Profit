# Drom Profit Core Backend

FastAPI хранит анонимные выборки в PostgreSQL. API использует [общий OpenAPI v0.1.0](../shared/openapi.yaml) и [правила расчета](../docs/calculation-rules.md).

## Локальный запуск через Docker Compose

Нужны Docker Engine со сборщиком образов, Docker Compose v2+ и клон репозитория вместе с `shared/`. Из корня перейдите в `backend/`. При первом запуске создайте локальный `.env` из примера и заполните `POSTGRES_PASSWORD` случайным значением. Сохраните файл для следующих запусков с тем же volume.

```bash
cd backend
(umask 077; cp -n .env.example .env)
# Укажите пароль в .env перед запуском
docker compose up --build -d
docker compose ps -a
```

При первой сборке Docker использует официальные образы Python и PostgreSQL, pip получает wheels из PyPI. В среде с ограниченным доступом это требует предварительного разрешения. Для запуска уже собранных образов задайте в `.env` значение `DROM_POSTGRES_PULL_POLICY=never` и используйте `docker compose up --no-build --pull never -d`.

Compose запускает PostgreSQL, дожидается его healthcheck и применяет `alembic upgrade head` в одноразовом сервисе `migrate`. Нормальное состояние этого сервиса после миграции — `Exited (0)`. Затем стартует API под UID 10001. Его HTTP-healthcheck проверяет ответ `/openapi.json`, а при старте само приложение проверяет доступ к таблице сессий. Для диагностики используйте `docker compose logs migrate backend`. Контракт и fixtures все сервисы Python читают из `../shared`, смонтированного только для чтения.

- REST доступен по [127.0.0.1:8000/api](http://127.0.0.1:8000/api/cars). Для начала работы создайте сессию через `POST /api/session`.
- [Swagger UI](http://127.0.0.1:8000/docs) и [OpenAPI JSON](http://127.0.0.1:8000/openapi.json) доступны без токена. Стандартный Swagger UI FastAPI загружает ресурсы в браузере с CDN. При запрете внешней сети используйте JSON или локальный YAML.
- API и БД опубликованы только на loopback. Данные PostgreSQL размещены в именованном volume. Внутренние сети рабочей и тестовой БД разделены.

### Разработка, миграции и остановка

```bash
docker compose -f compose.yaml -f compose.dev.yaml up --build -d
docker compose run --rm migrate
docker compose run --rm migrate python -m alembic check
docker compose down
```

В режиме разработки изменения `app/**/*.py` видны Uvicorn через bind mount с автоперезагрузкой. После изменений зависимостей или миграций пересоберите образ. Обычный `down` сохраняет данные в volume. Для временной остановки достаточно `docker compose stop`.

### Настройки

| Переменная | Назначение |
|---|---|
| `POSTGRES_PASSWORD` | Пароль локальной БД. Compose передает его контейнерам через окружение. |
| `DROM_DATABASE_URL`, `DROM_TEST_DATABASE_URL` | Для запуска с хоста используются loopback-порты 55432 и 55433. Контейнеры получают свои DSN из Compose. |
| `DROM_DB_POOL_SIZE`, `DROM_DB_MAX_OVERFLOW` | По умолчанию 10 постоянных и 5 дополнительных соединений на процесс. |
| `DROM_CORS_ORIGINS` | Точные origins Web через запятую, например `http://localhost:5173`. |

### Альтернативный запуск через venv

При таком запуске нужен Python 3.11+ и окружение с зависимостями backend. Команды выполняются из `backend/`. Для пустого окружения установка из локального wheelhouse выполняется один раз после получения пакетов. Запускайте этот вариант при свободном порте 8000.

```bash
source /tmp/drom-profit-core-venv/bin/activate  # либо активируйте свою .venv
python -m pip install --no-index --find-links="$WHEELHOUSE" -r requirements-dev.txt
set -a
. ./.env
set +a
export PGPASSWORD="$POSTGRES_PASSWORD"
docker compose up --pull never -d --wait postgres
python -m alembic upgrade head
python -m uvicorn app.main:app --host 127.0.0.1 --port 8000
```

### Транзакции

Синхронные обработчики FastAPI используют SQLAlchemy и Psycopg в рабочих потоках. Проверка Bearer завершается до выполнения бизнес-операции. Она освобождает соединение до ожидания асинхронного импортера. Пул общий для чтения и записи.

Изменение карточки или вложенного объекта выполняется в `READ COMMITTED`. Первым блокируется принадлежащий владельцу автомобиль через `SELECT … FOR UPDATE`. Расходы, преимущества и заметки загружаются после блокировки. Денежный фактор и сброс подтверждения фиксируются вместе. Операция записи затрагивает одну карточку, ожидание ее блокировки ограничено 3 с. Список, саммари и сравнение читают все части ответа в одной транзакции `REPEATABLE READ READ ONLY`.

### Перенос существующих данных

Первичная ревизия `0001_core` создает схему в новой БД. Сервис не читает и не удаляет старые SQLite-файлы. Если данные понадобятся, сначала остановите запись старой версии и сделайте согласованную резервную копию SQLite средствами SQLite backup, включая данные из WAL. Проверьте копию и перенос на отдельной PostgreSQL. До записи проверьте диапазоны, текст с нулевыми байтами, дубли ключей, ссылки и ограничения. В одной транзакции перенесите сессии, карточки и затем вложенные записи, сохранив ID, хеши токенов, NULL и флаги. Сверьте числа строк, контрольные суммы и результаты расчетов. Переключение DSN требует отдельного решения после проверки. Сохраните исходную копию для возврата; новые записи после переключения требуют отдельной сверки при откате.

## Проверка через Swagger

1. Выполните `POST /api/session`. Введите полученный токен в **Authorize** без префикса `Bearer`. Клиент хранит токен между запусками для доступа к своей выборке.
2. Для демонстрационного предпросмотра отправьте `{"url":"https://auto.drom.ru/demo/1.html"}` в `POST /api/cars/import`. Результат с `source=demo` берется из [синтетических данных](../shared/fixtures/import-listings.json). Для сохранения отправьте подтвержденные данные в `POST /api/cars`. При ручном вводе передайте `source=manual`, `brand`, `model` и `price`.
3. Добавьте расходы, преимущества и заметки во вложенных маршрутах. Введите `amount: null` для неизвестной суммы и `amount: 0` для известной нулевой суммы. После проверки данных подтвердите рассмотренные категории через `PATCH /api/cars/{car_id}`.
4. Получите карточку и `GET /api/cars/{car_id}/summary`. Для сравнения отправьте минимум два уникальных ID своих карточек в `POST /api/comparison`.

Сравнение возвращает элементы в порядке запроса. При неполных данных разницы предварительные. При двойном учете оснащения разницы возвращаются как `null`. Для конфликтов факторов внутри одной карточки API возвращает `422` с кодами `duplicate_factor` или `conflicting_equipment`.

## Импортер

При сборке приложения подключите `create_app(importer=import_listing)`. Интерфейс — `async import_listing(url: str) -> ListingData` из `app.schemas`, ошибки — `ListingImportError` из `app.services.listing_import`. Допустимые коды и соответствующие HTTP-статусы заданы в общем контракте. Предпросмотр работает без записи в БД. В режиме по умолчанию используются три синтетических URL из fixtures, сетевые запросы отсутствуют.

## Тесты

Тестовый контейнер использует сетевой namespace отдельного `postgres-test` и подключается к `drom_profit_test` по loopback. Каждый тест мигрирует собственную временную схему. Docker публикует тестовую БД на порту 55433 хоста для venv. Для первого запуска тестового образа нужна согласованная сборка.

```bash
docker compose --profile test build tests
docker compose --profile test up --pull never -d --wait postgres-test
docker compose --profile test run --rm tests
# После успешного функционального прогона
docker compose --profile test run --rm tests python -m pytest -q -s -p no:cacheprovider tests/load_postgres.py
docker compose --profile test stop postgres-test
```

При запуске через venv используйте те же PostgreSQL и окружение из раздела выше, затем `python -m pytest -q` и `python -m pytest -q -s tests/load_postgres.py`.

Нагрузка поднимает Uvicorn внутри тестового контейнера. Сценарии используют 1, 10, 50 и 100 конкурентных пользователей, по умолчанию по 5 циклов и 9 запросов за цикл. JSON-отчет содержит среднее время ответа, медиану, P95, P99, RPS, успешность, ошибки, конфликты и параметры среды. Для другого числа циклов задайте `DROM_LOAD_ROUNDS`.
