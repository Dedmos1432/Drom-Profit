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
docker compose -p drom-mvp-integration up --build -d --wait
docker compose -p drom-mvp-integration ps -a
```

Используйте один и тот же `-p drom-mvp-integration` для запуска, тестов, миграций и остановки. Compose использует это имя для собственных локальных образов и тома. Перед запуском проверьте, что порты 8000 и 55432 свободны. Порт 55433 требуется только для тестов из venv.

Политика `missing` позволяет Compose получить официальные образы PostgreSQL и Python для TCP-прокси при отсутствии локальной копии. При сборке Backend Docker использует базовый образ Python, а pip получает wheels из PyPI. Для автономного запуска заранее загрузите оба базовых образа и соберите образы проекта, затем выполните `docker compose -p drom-mvp-integration up --no-build --pull never -d --wait`.

Compose запускает PostgreSQL, дожидается его healthcheck и применяет `alembic upgrade head` в одноразовом сервисе `migrate`. Нормальное состояние этого сервиса после миграции — `Exited (0)`. Затем стартует API под UID 10001. Его HTTP-healthcheck проверяет ответ `/openapi.json`, а при старте само приложение проверяет доступ к таблице сессий. Для диагностики используйте `docker compose -p drom-mvp-integration logs migrate backend`. Контракт и fixtures все сервисы Python читают из `../shared`, смонтированного только для чтения.

- REST доступен по [127.0.0.1:8000/api](http://127.0.0.1:8000/api/cars). Для начала работы создайте сессию через `POST /api/session`.
- [Swagger UI](http://127.0.0.1:8000/docs) и [OpenAPI JSON](http://127.0.0.1:8000/openapi.json) доступны без токена. Стандартный Swagger UI FastAPI загружает ресурсы в браузере с CDN. При запрете внешней сети используйте JSON или локальный YAML.
- Порты хоста слушают только loopback. Сервисы `api-loopback`, `postgres-loopback` и `postgres-test-loopback` передают TCP в соответствующую внутреннюю сеть. API, PostgreSQL и тесты остаются во внутренних сетях Docker. Фактическую публикацию API показывает `docker compose -p drom-mvp-integration port api-loopback 8000`. Данные основной БД хранятся в именованном volume.

### Разработка, миграции и остановка

```text
docker compose -p drom-mvp-integration -f compose.yaml -f compose.dev.yaml up --build -d --wait
docker compose -p drom-mvp-integration run --rm migrate
docker compose -p drom-mvp-integration run --rm migrate python -m alembic check
docker compose -p drom-mvp-integration down
```

В режиме разработки изменения `app/**/*.py` видны Uvicorn через bind mount с автоперезагрузкой. После изменений зависимостей или миграций пересоберите образ. Обычный `down` сохраняет данные в volume. Для временной остановки достаточно `docker compose -p drom-mvp-integration stop`.

### Настройки

| Переменная | Назначение |
|---|---|
| `POSTGRES_PASSWORD` | Пароль локальной БД. Compose передает его контейнерам через окружение. |
| `DROM_DATABASE_URL`, `DROM_TEST_DATABASE_URL` | Для запуска с хоста используются loopback-порты 55432 и 55433. Контейнеры получают свои DSN из Compose. |
| `DROM_DB_POOL_SIZE`, `DROM_DB_MAX_OVERFLOW` | По умолчанию 10 постоянных и 5 дополнительных соединений на процесс. |
| `DROM_CORS_ORIGINS` | Точные origins Web через запятую, например `http://localhost:5173`. |
| `DROM_LISTINGS_SOURCE` | В интеграционном Backend используется `demo`. Пустое значение выбирает `demo`. Другие значения завершают запуск с ошибкой конфигурации. |
| `DROM_LISTINGS_FIXTURE` | При запуске вне Compose можно указать путь к разрешённому локальному JSON. Compose использует общий набор из read-only `../shared`. |

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
docker compose -p drom-mvp-integration up --pull never -d --wait postgres-loopback
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

Фабрика приложения создаёт адаптер один раз на экземпляр FastAPI. Штатный Uvicorn создаёт один экземпляр приложения и импортера на процесс. Адаптер вызывает `importer_from_env()`, который загружает три синтетические записи из `shared/fixtures/import-listings.json` при создании. Demo Provider использует локальные данные без HTTP-запросов. В тестах `create_app(importer=...)` по-прежнему принимает асинхронный callable, возвращающий `app.schemas.ListingData`. [Фабрика приложения](app/main.py) · [Адаптер](app/services/listing_import.py)

Маршрут проверяет тело и Bearer. Авторизация возвращает соединение PostgreSQL в пул до ожидания импортера. Адаптер преобразует неизменяемый dataclass через `ListingData.model_validate(data.as_dict())`. Предпросмотр доступен по `POST /api/cars/import`. Для записи используйте отдельный `POST /api/cars` с отредактированными данными. [Маршрут](app/api/cars.py) · [Авторизация](app/api/sessions.py)

| HTTP | Код | Условие |
|---|---|---|
| 400 | `invalid_url` | Строка URL не проходит проверку Core или Parser. |
| 401 | `unauthorized` | Bearer отсутствует или недействителен. |
| 403 | `source_forbidden` | Источник запретил доступ. |
| 404 | `listing_unavailable` | В demo-наборе отсутствует объявление с допустимым URL. |
| 422 | `validation_error` | Ошибка структуры запроса. |
| 502 | `parse_error` | Ошибка разбора либо результат не проходит валидацию Core. |
| 503 | `source_unavailable` | Ошибка доступности источника. |

Ответ ошибки содержит `code` и пользовательское `message`. Неожиданные исключения обрабатываются существующим обработчиком `500 internal_error`. В demo-режиме доступны `https://auto.drom.ru/demo/1.html`, `/demo/2.html` и `/demo/3.html` на том же хосте. Parser приводит регистр хоста к нижнему и отбрасывает query и fragment. Он требует HTTPS, точный хост и путь объявления с `.html`. Явные порты и учётные данные в URL отклоняются.

Публичная модель передаёт одно nullable-поле `image_url`. У всех трёх общих demo-записей оно равно `null`. Реальный источник требует отдельного согласования. Вопросы галереи, точности денег, `factor_key` и пула PostgreSQL перечислены в [интеграционном бэклоге](../docs/INTEGRATION_BACKLOG.md).

## Локальная проверка сохранённых объявлений

Шесть сохранённых страниц проверены без обращения к источнику. Существующий адаптер допускает внедрение Parser с локальным транспортом. Штатный Core принимает `DROM_LISTINGS_SOURCE=demo`. [Манифест, команды и результаты](../docs/REAL_LISTING_VALIDATION.md)

## Подключение Web на этапе 8.3

1. В локальном окружении Backend задайте `DROM_CORS_ORIGINS=http://localhost:5173` или точный origin вашего Web. Запустите Backend командой выше.
2. В рабочем дереве Web установите `VITE_API_MODE=real` и `VITE_API_BASE_URL=http://127.0.0.1:8000`. Выполните `npm ci` и `npm run dev` из каталога `web/`.
3. Проверьте анонимную сессию, предпросмотры `/demo/1.html`–`/demo/3.html`, редактирование, сохранение, восстановление сессии, расходы, преимущества, заметки, Summary, Comparison и ошибки. Ссылки витрины `101–105` обслуживает Web Mock до согласования серверного демонабора.

## Тесты

Тестовый контейнер использует сетевой namespace отдельного `postgres-test` и подключается к `drom_profit_test` по loopback. Каждый тест мигрирует собственную временную схему. Для запуска pytest из venv запустите сервис `postgres-test-loopback`, который публикует тестовую БД на порту 55433 хоста. Для первого запуска тестового образа нужна согласованная сборка.

```text
docker compose -p drom-mvp-integration --profile test build tests
docker compose -p drom-mvp-integration --profile test up -d --wait postgres-test
docker compose -p drom-mvp-integration --profile test run --rm --no-deps tests python -m pytest --collect-only -q -p no:cacheprovider
docker compose -p drom-mvp-integration --profile test run --rm --no-deps tests
docker compose -p drom-mvp-integration --profile test run --rm --no-deps tests python -m pytest -q -p no:cacheprovider tests/listings/test_listings_http_worker.py
docker compose -p drom-mvp-integration --profile test run --rm --no-deps tests python -m pytest -q -s -p no:cacheprovider tests/load_postgres.py -k 'not 100'
docker compose -p drom-mvp-integration --profile test stop postgres-test
```

Единый прогон собирает тесты Core, `tests/listings` и `tests/test_listing_integration.py`. Общий `conftest.py` использует `socket.AF_UNIX`. Для полного прогона на Windows используйте Linux-контейнеры. Отдельные тесты Parser могут запускаться без PostgreSQL в его исходном Python-окружении.

Нагрузка поднимает Uvicorn внутри тестового контейнера. Сценарии используют 1, 10, 50 и 100 конкурентных пользователей, по умолчанию по 5 циклов и 9 запросов за цикл. JSON-отчет содержит среднее время ответа, медиану, P95, P99, RPS, успешность, ошибки, конфликты и параметры среды. Для другого числа циклов задайте `DROM_LOAD_ROUNDS`.
