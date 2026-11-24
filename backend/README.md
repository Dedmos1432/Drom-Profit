# Drom Profit Core Backend

FastAPI хранит анонимные выборки в PostgreSQL. API использует [общий OpenAPI v0.1.0](../shared/openapi.yaml) и [правила расчета](../docs/calculation-rules.md).

## Локальный запуск через Docker Compose

На Linux нужен Docker Engine со сборщиком образов. На Windows и macOS используйте Docker Desktop. В Windows выберите режим Linux containers. Для публикации портов нужен Engine с поддержкой приоритета шлюза сети. Конфигурация проверена на Engine 29. Compose **2.33.1 или новее** поддерживает используемый параметр `gw_priority`. Версии сервера и Compose показывают `docker version` и `docker compose version`.

Клонируйте репозиторий целиком, вместе с `shared/`. Из корня перейдите в `backend/` командой `cd backend`. Все команды ниже выполняются из этого каталога.

### Создание локального .env

Команды создают `.env` из `.env.example` и записывают случайный пароль. Существующий файл остается прежним. Храните его для повторных запусков с тем же volume.

**Linux и macOS — Bash** (нужен `openssl`). Новый файл получает права `600`.

```bash
if [[ ! -e .env && ! -L .env ]]; then
  (
    umask 077
    set -o noclobber
    password=$(openssl rand -hex 32) &&
      sed "s/^POSTGRES_PASSWORD=$/POSTGRES_PASSWORD=$password/" .env.example > .env
  )
fi
```

**Windows — PowerShell**. Файл наследует права папки. Используйте личную папку с ограниченным доступом. Права можно проверить на вкладке **Безопасность** в свойствах файла.

```powershell
if (-not (Test-Path -LiteralPath .env)) {
    $bytes = [byte[]]::new(32)
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    $password = [BitConverter]::ToString($bytes).Replace('-', '').ToLowerInvariant()
    $template = Get-Content -LiteralPath .env.example -Raw
    $content = $template -replace '(?m)^POSTGRES_PASSWORD=(?=\r?$)', "POSTGRES_PASSWORD=$password"
    $stream = [IO.File]::Open((Join-Path $PWD.Path '.env'), [IO.FileMode]::CreateNew)
    $writer = [IO.StreamWriter]::new($stream)
    try { $writer.Write($content) } finally { $writer.Dispose() }
}
```

### Запуск

```text
docker compose up --build -d
docker compose ps -a
```

При первой сборке Docker использует официальные образы Python и PostgreSQL, pip получает wheels из PyPI. В среде с ограниченным доступом это требует предварительного разрешения. Для запуска уже собранных образов задайте в `.env` значение `DROM_POSTGRES_PULL_POLICY=never` и используйте `docker compose up --no-build --pull never -d`.

Compose запускает PostgreSQL, дожидается его healthcheck и применяет `alembic upgrade head` в одноразовом сервисе `migrate`. Нормальное состояние этого сервиса после миграции — `Exited (0)`. Затем стартует API под UID 10001. Его HTTP-healthcheck проверяет ответ `/openapi.json`, а при старте само приложение проверяет доступ к таблице сессий. Для диагностики используйте `docker compose logs migrate backend`. Контракт и fixtures все сервисы Python читают из `../shared`, смонтированного только для чтения.

- REST доступен по [127.0.0.1:8000/api](http://127.0.0.1:8000/api/cars). Для начала работы создайте сессию через `POST /api/session`.
- [Swagger UI](http://127.0.0.1:8000/docs) и [OpenAPI JSON](http://127.0.0.1:8000/openapi.json) доступны без токена. Стандартный Swagger UI FastAPI загружает ресурсы в браузере с CDN. При запрете внешней сети используйте JSON или локальный YAML.
- Порты хоста слушают только loopback. Сервисы `api-loopback`, `postgres-loopback` и `postgres-test-loopback` передают TCP в соответствующую внутреннюю сеть. API, PostgreSQL и тесты остаются во внутренних сетях Docker. Фактическую публикацию API показывает `docker compose port api-loopback 8000`. Данные основной БД хранятся в именованном volume.

### Разработка, миграции и остановка

```text
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

Нужен Python 3.11+ и свободный порт 8000. Подготовьте `.env` как описано выше. Для venv используются простые строки `ИМЯ=значение` из `.env.example`. PostgreSQL и прокси по-прежнему работают в Docker. Для автономного запуска заранее загрузите `postgres:17.6-bookworm` и `python:3.14.0-slim-bookworm` в локальное хранилище Docker.

**Linux и macOS — Bash**

```bash
python3 -m venv .venv
source .venv/bin/activate
set -a
. ./.env
set +a
export PGPASSWORD="$POSTGRES_PASSWORD"
```

**Windows — PowerShell**. Добавление Python из venv в `PATH` действует только в текущем окне и позволяет обойтись без запуска `Activate.ps1`.

```powershell
py -3 -m venv .venv
$env:Path = "$(Join-Path $PWD.Path '.venv/Scripts');$env:Path"
Get-Content -LiteralPath .env | ForEach-Object {
    if ($_ -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
        Set-Item -LiteralPath "Env:$($Matches[1])" -Value $Matches[2]
    }
}
$env:PGPASSWORD = $env:POSTGRES_PASSWORD
```

Далее команды одинаковы. Установка зависимостей нужна один раз. Для установки из локального каталога `wheelhouse/` используйте `python -m pip install --no-index --find-links=./wheelhouse -r requirements-dev.txt`.

```text
python -m pip install -r requirements-dev.txt
docker compose up --pull never -d --wait postgres-loopback
python -m alembic upgrade head
python -m uvicorn app.main:app --host 127.0.0.1 --port 8000
```

### Транзакции

Синхронные обработчики FastAPI используют SQLAlchemy и Psycopg в рабочих потоках. Проверка Bearer завершается до выполнения бизнес-операции. Она освобождает соединение до ожидания асинхронного импортера. Пул общий для чтения и записи.

Изменение карточки или вложенного объекта выполняется в `READ COMMITTED`. Первым блокируется принадлежащий владельцу автомобиль через `SELECT … FOR UPDATE`. Расходы, преимущества и заметки загружаются после блокировки. Денежный фактор и сброс подтверждения фиксируются вместе. Операция записи затрагивает одну карточку, ожидание ее блокировки ограничено 3 с. Список, саммари и сравнение читают все части ответа в одной транзакции `REPEATABLE READ READ ONLY`.

### Перенос существующих данных

Первичная ревизия `0001_core` создает схему в новой БД. Сервис не читает и не удаляет старые SQLite-файлы. Если данные понадобятся, сначала остановите запись старой версии и сделайте согласованную резервную копию SQLite средствами SQLite backup, включая данные из WAL. Проверьте копию и перенос на отдельной PostgreSQL. До записи проверьте диапазоны, текст с нулевыми байтами, дубли ключей, ссылки и ограничения. В одной транзакции перенесите сессии, карточки и затем вложенные записи, сохранив ID, хеши токенов, NULL и флаги. Сверьте числа строк, контрольные суммы и результаты расчетов. Переключение DSN требует отдельного решения после проверки. Сохраните исходную копию для возврата. При откате отдельно сверьте записи, созданные после переключения.

## Проверка через Swagger

1. Выполните `POST /api/session`. Введите полученный токен в **Authorize** без префикса `Bearer`. Клиент хранит токен между запусками для доступа к своей выборке.
2. Для демонстрационного предпросмотра отправьте `{"url":"https://auto.drom.ru/demo/1.html"}` в `POST /api/cars/import`. Результат с `source=demo` берется из [синтетических данных](../shared/fixtures/import-listings.json). Для сохранения отправьте подтвержденные данные в `POST /api/cars`. При ручном вводе передайте `source=manual`, `brand`, `model` и `price`.
3. Добавьте расходы, преимущества и заметки во вложенных маршрутах. Введите `amount: null` для неизвестной суммы и `amount: 0` для известной нулевой суммы. После проверки данных подтвердите рассмотренные категории через `PATCH /api/cars/{car_id}`.
4. Получите карточку и `GET /api/cars/{car_id}/summary`. Для сравнения отправьте минимум два уникальных ID своих карточек в `POST /api/comparison`.

Сравнение возвращает элементы в порядке запроса. При неполных данных разницы предварительные. При двойном учете оснащения разницы возвращаются как `null`. Для конфликтов факторов внутри одной карточки API возвращает `422` с кодами `duplicate_factor` или `conflicting_equipment`.

## Импортер

При сборке приложения подключите `create_app(importer=import_listing)`. Интерфейс — `async import_listing(url: str) -> ListingData` из `app.schemas`, ошибки — `ListingImportError` из `app.services.listing_import`. Допустимые коды и соответствующие HTTP-статусы заданы в общем контракте. Предпросмотр работает без записи в БД. В режиме по умолчанию используются три синтетических URL из fixtures, сетевые запросы отсутствуют.

## Тесты

Тестовый контейнер использует сетевой namespace отдельного `postgres-test` и подключается к `drom_profit_test` по loopback. Каждый тест мигрирует собственную временную схему. Для запуска pytest из venv запустите сервис `postgres-test-loopback`, который публикует тестовую БД на порту 55433 хоста. Для первого запуска тестового образа нужна согласованная сборка.

```text
docker compose --profile test build tests
docker compose --profile test up --pull never -d --wait postgres-test
docker compose --profile test run --rm tests
# После успешного функционального прогона
docker compose --profile test run --rm tests python -m pytest -q -s -p no:cacheprovider tests/load_postgres.py
docker compose --profile test stop postgres-test
```

При запуске через venv загрузите окружение из раздела выше, выполните `docker compose --profile test up --pull never -d --wait postgres-test-loopback`, затем `python -m pytest -q` и `python -m pytest -q -s tests/load_postgres.py`.

Нагрузка поднимает Uvicorn внутри тестового контейнера. Сценарии используют 1, 10, 50 и 100 конкурентных пользователей, по умолчанию по 5 циклов и 9 запросов за цикл. JSON-отчет содержит среднее время ответа, медиану, P95, P99, RPS, успешность, ошибки, конфликты и параметры среды. Для другого числа циклов задайте `DROM_LOAD_ROUNDS`.
