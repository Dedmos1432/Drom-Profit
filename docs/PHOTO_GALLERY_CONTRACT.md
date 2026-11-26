# Контракт фотогалереи · 8.5.1

Исходное состояние этапа — чистая ветка `integration/mvp` на `bf15f7d`, синхронизированная с `origin/integration/mvp`. В 0.2.0 HTML Parser извлекал массив, а нормализатор передавал в `ListingData` только основную ссылку. PostgreSQL хранила `cars.image_url`. В Web дополнительными снимками управлял отдельный поиск по ID витрины Mock.

OpenAPI 0.3.0 добавляет галерею в ответ `POST /api/cars/import` (`ListingData`) и в `GET /api/cars/{car_id}` (`CarDetail`). Список, ответы на запись и карточки сравнения используют `image_url`. Summary и Comparison передают расчёты, Web берёт основное фото сравниваемых автомобилей из списка.

## Формат и совместимость

```json
{
  "image_url": "https://images.example.test/car/1.jpg",
  "image_urls": [
    "https://images.example.test/car/1.jpg",
    "https://images.example.test/car/2.jpg"
  ]
}
```

Первый элемент массива — основной снимок. Повторы одной и той же ссылки удаляются с сохранением первого вхождения. Если фото отсутствуют, `image_url` равен `null`, а `image_urls` равен `[]`. Одна фотография даёт массив из одного элемента.

Старые запросы с одним `image_url` поддерживаются. Web читает старые ответы с одной фотографией. Если ответ содержит безопасный `image_url`, который отличается от первого допустимого элемента, Web использует только `image_url`. Иначе из валидных ссылок формируется галерея, а первая ссылка становится основной. Небезопасные ссылки и элементы неверного типа отбрасываются до попадания в кэш.

В схеме 0.2.0 у `ListingData` и `Car` стоит `additionalProperties: false`. Клиент со строгим разбором может отвергнуть новый предпросмотр или детальный ответ. Такие клиенты нужно обновить вместе с Backend. Для старых DTO, проверяющих абсолютный URI, также требуется поддержка локальных demo-путей.

## POST и PATCH

| Запрос | Семантика |
|---|---|
| POST, оба поля пропущены | Галерея пуста. |
| POST, только `image_url` | Непустая ссылка становится единственным снимком. `null` создаёт пустую галерею. |
| POST или PATCH, передан `image_urls` | Массив задаёт галерею целиком. При наличии `image_url` он должен совпасть с первым элементом, либо быть `null` для пустого списка. `image_urls: null` недопустим. |
| PATCH, оба поля пропущены | Текущая галерея сохраняется. |
| PATCH, только непустой `image_url` | Ссылка переносится на первое место или добавляется в начало. Относительный порядок остальных снимков сохраняется. |
| PATCH, `image_urls: []` или только `image_url: null` | Галерея очищается. |

Противоречивые, недопустимые или превышающие лимиты поля возвращают `422 validation_error`. Изменения фотографий записываются атомарно. Если новый основной снимок при старом PATCH дал бы 65 ссылок, сервер отклоняет операцию целиком. Изменение только фото сохраняет денежные подтверждения. Для прочих полей и явных флагов продолжают действовать правила частичного обновления карточки.

## Проверка ссылок и хранения

- Длина ссылки ограничена 2048 символами. Backend и Web принимают HTTPS-адрес с DNS-именем без порта и учётных данных. IP-литералы, локальные имена, управляющие символы, пробелы и недопустимое кодирование отклоняются. Demo может использовать путь `/demo/vehicles/<имя>.jpg` из существующего статического каталога Web. Допустимы строчные латинские буквы, цифры и дефисы в имени. Такой путь разрешается относительно каталога ресурсов Web. Другие относительные пути отклоняются на REST-границе.
- Parser разрешает относительные снимки в пределах конечного хоста объявления и сохраняет порядок основной галереи. При отсутствии её снимков применяются структурированные поля и запасной `og:image`. Снимки рекомендованных объявлений исключаются. До Core передаются первые 64 допустимые уникальные ссылки. Лимит больше максимума в 28 снимков на сохранённом образце и ограничивает размер одной галереи. Входной массив REST длиннее 64 элементов отклоняется до удаления повторов.
- Ссылки являются данными. Backend не запрашивает изображения и не разрешает DNS их хостов. Браузер загружает HTTPS-ресурс напрямую либо получает локальный JPG от Web. Лексическая проверка адреса не подтверждает DNS-назначение внешнего хоста или права на снимок.
- PostgreSQL хранит порядок в `cars.image_urls` типа `varchar[]`. ARRAY типизирует элементы и позволяет проверить длину и первый элемент обычными ограничениями. JSONB пригодился бы при появлении метаданных на снимок. Отдельная таблица с порядковым номером понадобится при независимом редактировании фотографий.
- Миграция `0003_car_gallery` после `0002_money_contract` переносит имеющийся `image_url` в массив из одного элемента и создаёт пустые массивы остальным карточкам. Сырые старые значения остаются в БД. Недопустимые по новой политике ссылки скрываются в REST. При downgrade сохраняется только основной снимок, а повторный upgrade восстанавливает только его.
- Список и расчётные запросы не загружают колонку галереи. В детальном запросе ORM загружает её вместе с карточкой. Соединение возвращается в пул до сериализации ответа FastAPI.

## Локальный запуск и ручная проверка

В каталоге Backend нужен локальный `.env` с `POSTGRES_PASSWORD`. Имя `drom-mvp-gallery` создаёт отдельный том PostgreSQL. API слушает loopback. [Подготовка пароля](../backend/README.md#создание-локального-env)

```powershell
Set-Location 'C:\DromProfit\Drom-Profit-integration\backend'
$env:DROM_LISTINGS_SOURCE = 'demo'
$env:DROM_CORS_ORIGINS = 'http://localhost:5173'
docker compose -p drom-mvp-gallery up --build -d --wait api-loopback
(Invoke-RestMethod http://127.0.0.1:8000/openapi.json).info.version
```

Во втором терминале запустите Web.

```powershell
Set-Location 'C:\DromProfit\Drom-Profit-integration\web'
npm.cmd ci
$env:VITE_API_MODE = 'real'
$env:VITE_API_BASE_URL = 'http://127.0.0.1:8000'
npm.cmd run dev -- --host localhost --port 5173 --strictPort
```

1. Откройте локальный Web и импортируйте `https://auto.drom.ru/demo/1.html`. Проверьте три снимка в предпросмотре, сохраните карточку и откройте её из списка. Проверьте миниатюры, стрелки и переключение клавишами.
2. Измените характеристику и убедитесь, что снимки сохранены. Добавьте расход и преимущество, проверьте Summary. Импортируйте `.../demo/2.html` с одним фото, добавьте обе карточки к сравнению и проверьте основные снимки. Для пустого состояния используйте `.../demo/3.html`.
3. Обновите страницу, затем выполните `docker compose -p drom-mvp-gallery restart backend` в терминале Backend и снова откройте карточку. Проверьте порядок фото на desktop и при ширине 390 px. Для теста недоступности на том же стенде можно заблокировать в DevTools запрос к одному локальному JPG и обновить карточку.
4. Для Mock Showcase остановите Web, очистите в новом терминале `VITE_API_BASE_URL`, задайте `$env:VITE_API_MODE = 'mock'` и `$env:VITE_MOCK_SCENARIO = 'showcase'`. Повторите запуск Vite. Откройте одну из пяти карточек и переключите снимки.

Demo Provider и Mock используют общий `shared/fixtures/import-listings.json` для трёх ссылок. Пять карточек Showcase используют свои данные Mock API в том же формате. JPG уже входят в каталог Web, где документированы источники иллюстраций. Backend передаёт только их пути. [Демо-материалы](../web/README.md)

Для остановки нажмите Ctrl+C в Web. В терминале Backend выполните `docker compose -p drom-mvp-gallery down`. Эта команда сохраняет тестовый том.

## Результаты 26 ноября 2026 года

- 434 серверных теста в Linux Docker на отдельной PostgreSQL 17.6. Проверены чистая установка и `alembic` upgrade с `0002_money_contract`, перенос старых снимков и отсутствие снимка, порядок, ограничение БД, downgrade и повторный upgrade. Прошли общие денежные тесты и шесть расчётных наборов на 13 автомобилях.
- 108 Web-тестов, TypeScript typecheck, ESLint и production build. При production build Rollup сообщил о двух аннотациях в зависимости Zod. Mock и Core проверены на одинаковом формате предпросмотра, детального ответа и ответов на запись.
- Google Chrome 158.0.8127.88, изолированные профили. В `real` проверены анонимная сессия, demo-предпросмотр и сохранение, фото в списке и Comparison, стрелки и миниатюры, правка цены без потери галереи, расходы, преимущества и Summary. Галерея сохранилась после перезагрузки и перезапуска Backend. При ширине 390 px страница помещалась в viewport, а локальный подставной 404 отключил один снимок. Отдельно проверены один снимок и заглушка при пустой галерее. В Mock Showcase проверены пять карточек и переключение фото при 390 px.

### Размеры HTTP-ответов

Измерены фактические тела JSON без сжатия в двух отдельных Compose-проектах. Для детальных ответов использованы одинаковые синтетические карточки. В версии 0.2.0 передавался только основной снимок из соответствующего набора.

| Ответ | 0.2.0, байт | 0.3.0, байт |
|---|---:|---:|
| Предпросмотр `demo/1.html` | 403 | 568 |
| Детальная карточка без фото | 439 | 455 |
| Детальная карточка с одним фото | 479 | 539 |
| Детальная карточка с тремя фото | 479 | 629 |
| Детальная карточка с 28 фото | 479 | 1754 |
| Список из четырёх карточек | 1881 | 1881 |

### Нагрузка на локальную PostgreSQL

Один Uvicorn, прежний пул 10+5, пять сценариев на клиента. Каждая карточка содержала 28 синтетических ссылок, а каждый сценарий проверял детальный GET с порядком фотографий, Summary и Comparison. Ссылки в нагрузочном тесте не загружались.

| Клиентов | Завершено сценариев | HTTP-запросов | P95, мс | Ошибок / PoolTimeout |
|---:|---:|---:|---:|---:|
| 1 | 5 | 50 | 64 | 0 / 0 |
| 10 | 50 | 500 | 270 | 0 / 0 |
| 50 | 250 | 2500 | 1659 | 0 / 0 |
| 100 | 500 | 5000 | 3097 | 0 / 0 |

После каждой волны `checked_out` и `pool_waiters` равнялись нулю. Открытых бездействующих транзакций не было. Для воспроизведения с профилем `test` задайте `-e DROM_LOAD_CLIENTS=1,10,50,100 -e DROM_LOAD_GALLERY_PHOTOS=28` в команде `docker compose run`. [Нагрузочный прогон](BACKEND_PERFORMANCE.md#воспроизведение)

## Android и условия дальнейшей проверки

Для `ListingData` и `CarDetail` предусмотрен `List<String>` в порядке источника. Старый ответ с одним `image_url` преобразуется в список из одной ссылки. Пустой массив показывает заглушку. При ошибке изображения остальные остаются доступными. Для demo Android нужен отдельно настроенный origin Web-ресурсов, доступный устройству или эмулятору. Основную фотографию списка можно отобразить без запроса детальной карточки.

DTO для записи должны различать пропущенные поля и явный `null` для `image_url`. OpenAPI Generator 7.17.0 с `jvm-retrofit2` сгенерировал `List<String>` для `ListingData` и `CarDetail`, nullable-список для `CarInput` и `CarPatch`, `String?` для `image_url` и `Long`/`Long?` для цены. Android-кода в текущих Git refs нет. Проверки Retrofit/сериализатора, совместимости строгих клиентов и APK относятся к INT-17. [Руководство генератора Kotlin](https://openapi-generator.tech/docs/generators/kotlin/)

Повторный офлайн-разбор шести локальных страниц передал **6 основных фотографий и 136 ссылок** через DTO Parser. Фактическое получение страниц и показ оригинальных фотографий Дрома ожидают письменного разрешения и согласования прав. [Проверка сохранённых страниц](REAL_LISTING_VALIDATION.md) · [Ограничения сетевого импорта](LIVE_LISTING_VALIDATION.md)

## Файлы этапа

`M` означает изменение, `A` — новый файл, `D` — удаление. Правки оставлены в рабочем дереве `integration/mvp`.

```text
M  backend/app/api/cars.py
M  backend/app/formats.py
M  backend/app/models.py
M  backend/app/schemas.py
M  backend/app/services/listings/models.py
M  backend/app/services/listings/normalize.py
M  backend/app/services/listings/README.md
A  backend/migrations/versions/0003_car_gallery.py
M  backend/README.md
M  backend/tests/listings/test_listings_html.py
M  backend/tests/listings/test_listings_importer.py
M  backend/tests/listings/test_listings_normalize.py
M  backend/tests/listings/test_review_saved_listing.py
M  backend/tests/load_postgres.py
A  backend/tests/test_car_gallery.py
M  backend/tests/test_connection_lifecycle.py
M  backend/tests/test_contract.py
M  backend/tests/test_listing_integration.py
M  backend/tests/test_migrations.py
M  backend/tests/test_validation.py
M  docs/architecture.md
M  docs/INTEGRATION_BACKLOG.md
M  docs/INTEGRATION_RUNBOOK.md
A  docs/PHOTO_GALLERY_CONTRACT.md
M  docs/REAL_LISTING_VALIDATION.md
M  shared/fixtures/import-listings.json
M  shared/openapi.yaml
M  web/docs/BACKEND_INTEGRATION.md
M  web/src/app/real-integration.test.tsx
M  web/src/entities/vehicle/api.ts
A  web/src/entities/vehicle/photos.ts
M  web/src/entities/vehicle/presentation.ts
D  web/src/entities/vehicle/showcase-photos.ts
M  web/src/entities/vehicle/validation.ts
M  web/src/entities/vehicle/VehicleGallery.module.css
M  web/src/entities/vehicle/VehicleGallery.test.tsx
M  web/src/entities/vehicle/VehicleGallery.tsx
M  web/src/entities/vehicle/VehiclePhoto.tsx
M  web/src/features/add-vehicle/useSaveVehicle.ts
M  web/src/features/add-vehicle/VehicleForm.tsx
M  web/src/mocks/handlers.test.ts
M  web/src/mocks/handlers.ts
M  web/src/mocks/showcase.ts
M  web/src/mocks/store.ts
M  web/src/mocks/vehicle-writes.ts
M  web/src/pages/add-car/AddCarPage.module.css
M  web/src/pages/add-car/AddCarPage.tsx
M  web/src/pages/car-details/CarDetailsPage.tsx
M  web/src/shared/api/schema.ts
```
