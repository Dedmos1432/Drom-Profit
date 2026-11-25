# Локальный запуск Дром Профит

Инструкция для объединённой ветки `integration/mvp`. Web работает на Vite, Backend и PostgreSQL запускаются через Docker Compose. Режим Web `real` обращается к локальному REST API. Backend импортирует синтетические записи через Demo Parser.

## Требования

- Docker Engine или Docker Desktop с Linux-контейнерами. Нужен Docker Compose 2.33.1 или новее.
- Node.js 20.x начиная с 20.19, 22.x начиная с 22.13 либо 24 и новее. Также нужны npm и браузер.
- Свободные loopback-порты 8000 и 55432 для Backend и PostgreSQL и 5173 для Web. Порт 55433 нужен для тестов PostgreSQL с хоста.

## Терминал 1. Backend и PostgreSQL

Выполните команды PowerShell из корня объединённого репозитория. При первом запуске создайте локальный `backend/.env` с паролем по README Backend. Сохраните его для повторного использования с тем же томом. [Создание .env для PowerShell и Bash](../backend/README.md#создание-локального-env)

```powershell
Set-Location backend
$env:DROM_LISTINGS_SOURCE = 'demo'
$env:DROM_CORS_ORIGINS = 'http://localhost:5173'
docker compose -p drom-mvp-integration up --build -d --wait
docker compose -p drom-mvp-integration ps -a
Invoke-RestMethod http://127.0.0.1:8000/openapi.json | Select-Object -ExpandProperty info
```

PostgreSQL ожидает healthcheck, затем Compose запускает миграции Alembic. Для сервиса `migrate` нормален статус `Exited (0)`. Версия OpenAPI в ответе — `0.1.0`. Именованный том хранит сессии и карточки между запусками.

## Терминал 2. Web

В отдельном терминале, из корня объединённого репозитория:

```powershell
Set-Location web
npm.cmd ci
$env:VITE_API_MODE = 'real'
$env:VITE_API_BASE_URL = 'http://127.0.0.1:8000'
npm.cmd run dev -- --host localhost --port 5173 --strictPort
```

Откройте [Дром Профит](http://localhost:5173/). В Linux и macOS используйте `npm` вместо `npm.cmd` и задайте те же переменные через `export`. Dev и build автоматически генерируют CSS-токены. Типы OpenAPI включены в репозиторий. `npm run api:generate` нужен при согласованном обновлении контракта.

## Проверка сценария

1. При первом открытии увидите пустую выборку новой анонимной сессии. Откройте «Добавить автомобиль» или вставьте ссылку через форму быстрого добавления.
2. Выберите один из трёх синтетических адресов и получите предпросмотр:
   - `https://auto.drom.ru/demo/1.html`
   - `https://auto.drom.ru/demo/2.html`
   - `https://auto.drom.ru/demo/3.html`
3. Измените цену или характеристики и явно сохраните автомобиль. Внесите расходы, преимущества и заметки. Подтвердите обе группы и проверьте экономическую оценку.
4. Сохраните второй автомобиль. Выберите обе карточки и проверьте сравнение. Перезагрузите страницу, чтобы проверить восстановление сессии и выборки.
5. В Chrome DevTools откройте Network и отфильтруйте `/api/`. Запросы идут к `http://127.0.0.1:8000`. Ожидаются `POST /api/session` → 201 при первом запуске, `POST /api/cars/import` → 200 и отдельный `POST /api/cars` → 201 после сохранения.

Для самостоятельной демонстрации Web используйте `VITE_API_MODE=mock` и `VITE_MOCK_SCENARIO=showcase`. Пять дополнительных demo-ссылок `101–105` обслуживает Mock API. [Независимый запуск Web](../web/README.md#проверка-в-mock-режиме)

## Типовые ошибки

| Симптом | Что проверить |
|---|---|
| Compose не подключается к движку | Docker Desktop запущен и использует Linux-контейнеры. |
| Vite сообщает, что порт занят | Остановите собственный старый процесс либо выберите свободный порт и задайте соответствующий точный `DROM_CORS_ORIGINS`. |
| Запрос заблокирован CORS | Сверьте origin из адресной строки и `DROM_CORS_ORIGINS`. `http://localhost:5173` и `http://127.0.0.1:5173` различаются. Пересоздайте Backend через `docker compose up -d --wait` с тем же именем проекта после смены настройки. |
| Запросы уходят на адрес с двойным `/api` | В `VITE_API_BASE_URL` укажите `http://127.0.0.1:8000`. Префикс содержится в путях клиента. |
| Demo-импорт возвращает 404 | Проверьте номер демозаписи. Backend обслуживает `1–3`, Web Mock также обслуживает `101–105`. |
| API возвращает 401 | Интерфейс предложит явно начать новую сессию. Новая сессия получит отдельную выборку. |
| API недоступен после запуска | Проверьте `docker compose -p drom-mvp-integration ps -a` и `docker compose -p drom-mvp-integration logs migrate backend`. |

## Остановка

Остановите Vite через Ctrl+C. Из `backend/` остановите и удалите контейнеры своего проекта, сохранив данные в томе.

```powershell
docker compose -p drom-mvp-integration --profile test down
```

Для серверных тестов используйте отдельный PostgreSQL из профиля `test`. Проверки Web запускаются командами `npm run typecheck`, `npm run lint`, `npm run test` и `npm run build`. [Команды серверных тестов](../backend/README.md#тесты)
