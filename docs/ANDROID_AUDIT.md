# Дром Профит — аудит Android перед интеграцией

Этап 8.7.1 · 26 ноября 2026 года · Android `2bc1ea8` · интеграция `7190cf2`

**Вердикт — Android готов к интеграции после ограниченного набора исправлений.** Compose-экраны, сценарии MVP и настоящий сетевой клиент уже есть. Неизменённый сетевой код Android на JVM получил успешные ответы по всем 18 операциям Backend 0.3.0. При импорте каждой из пяти машин Android сохраняет только первое из трёх фото. До сквозной приёмки нужны исправления галереи, денежной валидации и debug-конфигурации HTTP, а также сборка и прогон APK на Android. Локальная попытка собрать APK остановилась на отсутствии Android SDK до компиляции приложения.

## 1. Git и источники требований

| Worktree на начало аудита | HEAD и статус |
| --- | --- |
| `C:\DromProfit\Drom-Profit-android` | `2bc1ea8012d9550b165784697576b352121f8f51`, detached HEAD, рабочее дерево чистое. `origin/android-main` и `origin/android-dev` указывают на этот коммит. |
| `C:\DromProfit\Drom-Profit-integration` | `7190cf200809f08c5471b09e37c5f51e2f51222a`, `integration/mvp` и `origin/integration/mvp`, рабочее дерево чистое. |
| Основной worktree | `986d4d3`, `feat/web-init`. `git worktree list --porcelain` также показал отдельные worktree Backend и Parser. |

Обе команды `git merge-base HEAD origin/main` и `git merge-base HEAD integration/mvp` в Android-worktree вернули `c83ef783d8d2fcd1ce5357a897b52a1d75083068`. Это также локальный `origin/main`. История `git log --oneline origin/main..HEAD` содержит `49f1547 ver 1.0` и `2bc1ea8 ver 2`. По `git diff --stat origin/main...HEAD` итог Android — 113 новых файлов, 8 348 добавленных строк. `git diff --name-status` от общей базы с каждой из двух целевых веток показывает только `A android/…`. Добавлены проект и Wrapper, приложение, debug-репозиторий и 15 JPG с атрибуцией, тесты, README и пять файлов `android/.idea/`. Изменений в Backend, Web, OpenAPI, shared fixtures, корневом и Android `AGENTS.md` со стороны Android нет.

Сверены [общие инструкции](../AGENTS.md), [Android-инструкции](../android/AGENTS.md), [MVP](MVP.md), [архитектура](architecture.md), [правила расчёта](calculation-rules.md), [OpenAPI](../shared/openapi.yaml), [денежный контракт](MONEY_CONTRACT.md), [контракт галереи](PHOTO_GALLERY_CONTRACT.md), [витрина](DEMO_SHOWCASE.md), [бэклог](INTEGRATION_BACKLOG.md), [runbook](INTEGRATION_RUNBOOK.md) и [fixtures](../shared/fixtures/README.md). В интеграции базовая спецификация MVP дополняется OpenAPI с `info.version: 0.3.0` и поздними контрактами. Локальная копия OpenAPI в Android-ветке имеет версию `0.1.0`. Она унаследована из общей базы.

В fixtures интеграции есть 6 наборов расчёта, 3 базовых demo-импорта, 3 demo-карточки, 5 дополнительных объявлений `101–105` и 5 наборов оценок витрины, а также отдельные векторы денежного контракта. 445 Backend-тестов и 111 Web-тестов — результаты из отчёта витрины. На этом этапе выполнены адресные проверки Android и локального API.

## 2. Архитектура, модули и зависимости

Все пути к Kotlin ниже, если не указано иначе, относительны к `android/app/src/main/java/ru/dedmos/dromprofit/` в `2bc1ea8`. Файлы сборки и ресурсы указаны от корня репозитория.

| Область | Фактическое устройство |
| --- | --- |
| Сборка | Один модуль `:app`, Kotlin / Gradle Kotlin DSL. Wrapper 9.4.1, AGP 9.2.1, Compose Compiler plugin 2.2.10, встроенный Kotlin AGP 2.2.10, KSP 2.3.4. Java source/target 11, JVM Gradle Daemon закреплена на JDK 21. `compileSdk 37`, `targetSdk 36`, `minSdk 26`. `android/app/build.gradle.kts:33–70`, `android/gradle/libs.versions.toml`, `android/gradle/gradle-daemon-jvm.properties`. |
| UI и маршруты | Jetpack Compose, Material 3, BOM 2026.02.01, Navigation Compose 2.9.8. Одна `MainActivity`, `ProfitApp` связывает выборку, добавление, карточку и сравнение. XML используется для манифестов, настроек, иконок и темы запуска. Повторно используются компоненты, тема, форма и диалоги записей. |
| Состояние | Hilt ViewModel для четырёх экранов, `StateFlow`, `viewModelScope`, lifecycle-aware подписка. Формы и часть навигационного состояния используют `rememberSaveable`. Сценарии в `domain/usecases` обращаются к `CarRepository`. |
| DI | Hilt 2.59.2, `DromProfitApplication`, `di/DataModule.kt` и фабрики из source sets debug/release. |
| REST и JSON | Retrofit 3.0.0 поверх OkHttp, suspend-вызовы, явный `Authorization`. Ручное чтение и построение JSON через Gson 2.11.0, в `data/ContractJson.kt`. UI работает с доменными Kotlin-моделями. |
| Хранилище | Room 2.8.4, схема 1, поля `CarEntity` и JSON вложенных записей. В demo Room хранит выборку; есть перенос старого локального JSON. В real Room зеркалирует ответы, сервер остаётся источником данных. `AtomicFile` в `noBackupFilesDir` хранит токен API. `SelectionIdsStore` хранит состав сравнения в приватных SharedPreferences. |
| Ошибки | `RepositoryException` несёт код и текст. Раздельные состояния загрузки, ошибок импорта, карточки, Summary, сравнения и мутаций. Доступны повторы чтения, ручной ввод при ошибке импорта и явное начало новой сессии при 401. |
| Автотесты | 5 JVM-тестов `NetworkCarRepositoryTest` с перехватчиком OkHttp, 1 `DemoEconomicsTest` на общих векторах и 1 шаблонный JVM-тест. В исходниках также 2 инструментальных теста Room/debug и 1 шаблонный инструментальный тест. Экраны имеют Compose Preview. |

Существующая граница `presentation → domain → repository` достаточна для текущего MVP. Изменения локализуются в моделях, JSON-маппинге, загрузчике изображений, формах и тестах. Отдельный доменный слой расчёта для real-режима не требуется.

## 3. Сборка и окружение

Windows 11 amd64, Git 2.47.1. На хосте найдены Oracle JDK 25 и Microsoft OpenJDK 17.0.17. Для проверок Wrapper запускался с Oracle JDK 25, daemon использовал скачанный в отдельный временный Gradle home Temurin 21.0.7+6. При первоначальной ограниченной попытке загрузка Wrapper падала с `UnknownHostException: services.gradle.org`. После разрешённой загрузки Wrapper распаковал Gradle 9.4.1. Каталог Android SDK, платформа `android-37`, Build Tools, `adb`, `emulator` и AVD в проверенных путях отсутствовали. `ANDROID_HOME` и `ANDROID_SDK_ROOT` не заданы, `android/local.properties` отсутствует. Список устройств через `adb devices` получить было невозможно. Для AGP 9.2 по умолчанию нужны Build Tools 36.0.0. [Документация AGP 9.2](https://developer.android.com/build/releases/agp-9-2-0-release-notes)

| Фактическая команда из `android` | Результат |
| --- | --- |
| `.\gradlew.bat --version` | 0 после загрузки. Gradle 9.4.1, Launcher JVM 25, Daemon JVM с требованием Java 21. Kotlin 2.3.0 в выводе — версия самого Gradle. |
| `.\gradlew.bat --console=plain :app:tasks --all` | 0, проект сконфигурирован. Подтверждены задачи `assembleDebug`, `testDebugUnitTest`, `lintDebug`, `connectedDebugAndroidTest` и `assembleRelease`. Предупреждение о deprecated `assets.srcDir` в `app/build.gradle.kts:50`. |
| `.\gradlew.bat --console=plain :app:assembleDebug` | 1, `SDK location not found` при определении зависимостей `compileDebugJavaWithJavac`. |
| `.\gradlew.bat --console=plain :app:testDebugUnitTest` | 1, `SDK location not found` до запуска тестов. |
| `.\gradlew.bat --console=plain :app:lintDebug` | 1, `SDK location not found` до анализа `lintReportDebug`. |
| `:app:connectedDebugAndroidTest`, установка и запуск | Не запускались. Требуются SDK, APK и устройство либо AVD. |
| Отдельная компиляция Kotlin и JUnit | `OK (7 tests)`. Использован JVM-компилятор Kotlin 2.3.0 из Wrapper с `-language-version 2.2 -jvm-target 11` и реальные версии библиотек. CLI на JDK 25 предупредил об устаревшем `sun.misc.Unsafe` внутри компилятора. Android/Compose/KSP в этой проверке не участвовали. |

Для временной JVM-проверки вместо Android-реализаций `TextFile` и Room были переданы файловый токен и отключённое зеркало. Скомпилированы оригинальные исходники DTO, `ContractJson.kt`, `ProfitApi.kt`, `NetworkCarRepository.kt`, необходимые use cases, demo-экономика и существующие JVM-тесты. Скрипт и зависимости находились вне Git. Это подтверждает выполнение проверенного Kotlin-кода при данных зависимостях. Сборка debug и release остаётся отдельным условием приёмки. Ожидаемый APK `android/app/build/outputs/apk/debug/app-debug.apk` не создан.

## 4. Матрица покрытия MVP

Пометка «JVM + API» означает вызов реального Backend через исходный сетевой репозиторий. Compose-экран либо действие проверены чтением кода. Для всех строк запуск UI на устройстве имеет статус «не удалось проверить».

| Сценарий | Статус | Экран и прикладная логика | Подтверждение Backend |
| --- | --- | --- | --- |
| Анонимная сессия | Реализовано | Автоматическое создание при первом запросе выборки. | JVM + API, 201. |
| Сохранение сессии | Реализовано | `AtomicFile` вне backup, ключ по адресу API. | Повторное создание репозитория с прежним файлом токена вернуло ту же выборку. Android `AtomicFile` проверен по коду. |
| Список автомобилей | Реализовано | `SelectionScreen`, пустое состояние, загрузка и повтор. | JVM + API, пустая и заполненная выборки. |
| Импорт по ссылке | Реализовано | Быстрый ввод и отдельная форма, локальная проверка ссылки. | JVM + API, пять ссылок Demo Provider. |
| Предпросмотр импорта | Реализовано частично | Отдельный черновик и основное фото. Массив фотоснимков отсутствует. | JVM + API, импорт оставляет выборку прежней. |
| Редактирование до сохранения | Реализовано | `VehicleForm` заполняется полями предпросмотра. | JVM + API, перед сохранением изменён город всех пяти машин. |
| Отдельное сохранение машины | Реализовано | Кнопка сохраняет через `createCar`. | JVM + API, правильный новый ID и карточка. Галерея — P0-01. |
| Ручное добавление | Реализовано | Форма марки, модели, цены и необязательных полей. | JVM + API, два автомобиля с пограничными ценами. |
| Карточка автомобиля | Реализовано | `CarDetailScreen`, характеристики, записи, оценка. | JVM + API, отдельный GET и актуальные записи. |
| Галерея фотографий | Реализовано частично | Переключаемые локальные debug-фото. В real отсутствует массив кадров. | JVM + API воспроизвёл потерю 2 из 3 снимков при каждой записи. |
| Редактирование характеристик | Реализовано | `EditVehicleDialog`, PATCH изменённых полей. | JVM + API, город и явный null. Фото контрольной карточки сохранились. |
| Удаление автомобиля | Реализовано | Действие с подтверждением в выборке. | JVM + API, 204 и повторное чтение пустой выборки. |
| Добавление расхода | Реализовано | Диалог типа, суммы, комментария и ключа. | JVM + API, 201, в том числе сумма 0. |
| Редактирование расхода | Реализовано | Тот же диалог с текущими полями. | JVM + API, 200, переход 0 → null. |
| Удаление расхода | Реализовано | Диалог подтверждения, перечитывание карточки. | JVM + API, 204 и сброс флага. |
| Добавление преимущества | Реализовано | Диалог суммы и комментария. | JVM + API, 201, в том числе сумма L. |
| Редактирование преимущества | Реализовано | Тот же диалог с текущими полями. | JVM + API, 200, переход к null и изменение `factor_key`. |
| Удаление преимущества | Реализовано | Диалог подтверждения и перечитывание. | JVM + API, 204 и сброс флага. |
| Добавление заметки | Реализовано | Тип и текст заметки в карточке. | JVM + API, 201. |
| Редактирование заметки | Реализовано | Правка типа и текста. | JVM + API, 200. |
| Удаление заметки | Реализовано | Диалог подтверждения. | JVM + API, 204. |
| Неизвестные денежные значения | Реализовано | Переключатель неизвестной суммы, отдельное отображение null и 0. | JVM + API, обе формы значения, счётчики неизвестных. |
| Подтверждение расходов | Реализовано | «Все расходы учтены» / пересмотр. | JVM + API, независимый PATCH флага. |
| Подтверждение преимуществ | Реализовано | «Все преимущества учтены» / пересмотр. | JVM + API, отдельный флаг, старое значение другого сохранено. |
| Подтверждение пустых групп | Реализовано | «Расходов нет» и «Преимуществ нет». | JVM + API, обе пустые подтверждённые группы дают `complete`. |
| Summary | Реализовано | Оценка в карточке, отдельная загрузка и повтор. | JVM + API, точные производные суммы и полнота. |
| Comparison | Реализовано | Таблица, предупреждения, исключение из набора, переход к карточке. | JVM + API, расчёт для 2 и 5 машин, `difference_from_min` и его null. |
| Выбор двух и более | Реализовано | Чекбоксы, выбор всех, порог 2, проверка наличия ID. | JVM + API, запросы на 2 и 5 машин. |
| Предварительная оценка | Реализовано | Бейджи полноты и предварительного сравнения, объяснения предупреждений. | JVM + API, неполные записи, отрицательный E, `double_count`. |
| Обработка ошибок | Реализовано частично | Есть сетевые состояния, ручная форма, повтор и выход из 401. Сообщение о денежном лимите в форме машины теряется. | JVM + API, 401, 404 и `422 amount_out_of_range`. Обрыв сети и таймаут на устройстве остаются в приёмке. |
| Восстановление после перезапуска | Реализовано | Токен, Room и выбранные ID сохраняются. Открытие экрана после перезапуска APK не проверено. | Новый экземпляр репозитория с прежним токеном увидел данные. Чужая сессия их не получила. |

## 5. Совместимость OpenAPI 0.3.0

Скриптом сопоставлены пары HTTP-метод/маршрут из YAML и аннотаций Retrofit. Совпали 18 из 18 пар, лишних маршрутов нет. У 17 защищённых методов есть заголовок, `POST /api/session` анонимен. `car_id`, `item_id` и `car_ids` передаются строками. OpenAPI объявляет эти ID строковыми, Backend создаёт UUID, UUID созданных машин проверены в прогоне. Клиент передаёт полученные ID без преобразования. Имена, enum и nullable wire-поля читаются вручную по `snake_case`. Успешные HTTP-статусы ниже зафиксированы в JVM-прогоне.

| № | Метод и маршрут | Запрос → ответ | Android и результат |
| --- | --- | --- | --- |
| 1 | `POST /api/session` | Без тела → `Session`, 201 | `ProfitApi.kt:15`, `NetworkCarRepository.kt:220–230`. `token` и `token_type: bearer` прочитаны. |
| 2 | `POST /api/cars/import` | `ImportRequest {url}` → `ListingData`, 200 | `ProfitApi.kt:27`, `ContractJson.kt:117–136`. Ответ принят, `image_urls` отброшен. |
| 3 | `POST /api/cars` | `CarInput` → `Car`, 201 | `ProfitApi.kt:33`, `ContractJson.kt:64–83`. Марка, модель, цена и nullable поля совместимы. `image_urls` не передаётся. |
| 4 | `GET /api/cars` | Без тела → массив `Car`, 200 | `ProfitApi.kt:18`, `NetworkCarRepository.kt:62–65`. Корневой массив и основное фото совместимы. |
| 5 | `GET /api/cars/{car_id}` | Без тела → `CarDetail`, 200 | `ProfitApi.kt:21`, `ContractJson.kt:138–145`. Работает как `Car`, массив галереи теряется. |
| 6 | `PATCH /api/cars/{car_id}` | `CarPatch` → `Car`, 200 | `ProfitApi.kt:39`, `ContractJson.kt:85–95`. Поля характеристик и флаги совместимы. Обновление массива отсутствует. |
| 7 | `DELETE /api/cars/{car_id}` | Без тела → 204 | `ProfitApi.kt:46`, `NetworkCarRepository.kt:244–246`. Пустое тело обработано. |
| 8 | `POST /api/cars/{car_id}/expenses` | `ExpenseInput` → `Expense`, 201 | `ProfitApi.kt:52`, `ContractJson.kt:97–103,147–154`. `kind` и nullable `amount` совместимы. |
| 9 | `PATCH /api/cars/{car_id}/expenses/{item_id}` | `ExpensePatch` → `Expense`, 200 | `ProfitApi.kt:59`. Отправляет полный отредактированный объект, включая разрешённый null. |
| 10 | `DELETE /api/cars/{car_id}/expenses/{item_id}` | Без тела → 204 | `ProfitApi.kt:67`. Пустое тело обработано. |
| 11 | `POST /api/cars/{car_id}/benefits` | `BenefitInput` → `Benefit`, 201 | `ProfitApi.kt:74`, `ContractJson.kt:105–110,156–162`. Nullable `amount` и `factor_key` совместимы. |
| 12 | `PATCH /api/cars/{car_id}/benefits/{item_id}` | `BenefitPatch` → `Benefit`, 200 | `ProfitApi.kt:81`. Отправляет полный отредактированный объект, включая разрешённый null. |
| 13 | `DELETE /api/cars/{car_id}/benefits/{item_id}` | Без тела → 204 | `ProfitApi.kt:89`. Пустое тело обработано. |
| 14 | `POST /api/cars/{car_id}/notes` | `NoteInput` → `Note`, 201 | `ProfitApi.kt:96`, `ContractJson.kt:112–115,164–168`. Тип заметки и текст совместимы. |
| 15 | `PATCH /api/cars/{car_id}/notes/{item_id}` | `NotePatch` → `Note`, 200 | `ProfitApi.kt:103`. Отправляет `kind` и `text`. |
| 16 | `DELETE /api/cars/{car_id}/notes/{item_id}` | Без тела → 204 | `ProfitApi.kt:111`. Пустое тело обработано. |
| 17 | `GET /api/cars/{car_id}/summary` | Без тела → `Summary`, 200 | `ProfitApi.kt:118`, `ContractJson.kt:170–180`. Читаются все поля; типы производных см. раздел 6. |
| 18 | `POST /api/comparison` | `ComparisonRequest {car_ids}` → `Comparison`, 200 | `ProfitApi.kt:124`, `ContractJson.kt:182–197`. Плоский `ComparisonItem`, `warnings` и `provisional` совместимы. |

`NetworkCarRepository.kt:248–261` извлекает `Error.code` и `Error.message` из тела, имеет fallback при неожиданном ответе и отдельное действие для 401. Gson читает дерево, мапперы выбирают конкретные ключи. Лишнее поле не вызывает ошибку, что подтверждено реальным импортом и GET с `image_urls`. Отсутствующие nullable поля принимаются как null, отсутствующие обязательные значения, массивы и флаги карточки приводят к `invalid_response`. Новое значение закрытого enum также будет ошибкой формата. Матрица несовпадений и исправления приведены в разделе 11. Адаптация требуется на стороне Android.

## 6. Денежный контракт и экономическая модель

- Цена автомобиля, оценка, расходы и преимущества представлены `Long` / `Long?`. В `Summary` цена также `Long`. В пяти производных полях — `total_expenses`, `total_benefits`, `acquisition_cost`, `adjusted_cost`, `difference_from_min` — Android использует `BigInteger` / `BigInteger?`. Это отклонение от согласованного Kotlin-представления, но диапазоны `int64` текущего API переданы точно. `Int` используется для счётчиков неизвестных, `Double` — для объёма двигателя. `domain/Car.kt`, `domain/CarRecords.kt`, `domain/Comparison.kt:3–26`, `data/ContractJson.kt:170–190,217–226`.
- Gson отправляет `Long` целочисленным литералом. Чтение денег идёт из числового JSON-токена через `toLong()` или `toBigInteger()`. Преобразования через `Float` или `Double` и округление денег не обнаружены. `Components.kt:207–212` применяет целочисленный формат `ru-RU` и отображает знак отрицательного значения. Визуальное отображение на Android не запускалось.
- При `L = 1 000 000 000 000` ₽ JVM-прогон подтвердил цену и оценку L, сумму фактора L, C = 2L, E = −999 999 999 999, разницу = 2 999 999 999 999, `null` и 0 как разные значения, отклонение L+1 и нулевой оценки. Превышение R отклонено с `422 amount_out_of_range`. Исходная запись после ошибки сохранилась.
- `CarInput.changesFrom` удаляет неизменённые поля и исключает служебные `source`, `listing_id` и `fetched_at`. Очистка nullable характеристики отправляет явный null. PATCH одного review-флага оставляет другой прежним. Для форм расходов и преимуществ PATCH отправляет всю запись с nullable суммой, ключом и комментарием. Форма сохраняет исходные значения незатронутых полей, но протокол не различает их с полями, которые пользователь действительно менял. Для точной частичной правки понадобится сравнение с исходной записью. Live-проверка подтвердила 0 → null, сохранение цены и галереи при обычной правке, а также отдельное подтверждение групп.
- Стоимость C = P + R, E = C − B, суммы известных, неизвестные счётчики, статус, разницы и предупреждения поступают от Backend в real. `CarDetailViewModel` перечитывает карточку и Summary после денежных изменений, `ComparisonViewModel` использует серверное сравнение. UI показывает `incomplete`, `double_count`, `negative_adjusted_cost` и предварительный минимум. При `double_count` разницы равны null. `DemoEconomics.kt` находится в source set debug и воспроизводит сервер для автономного demo. Все шесть общих расчётных наборов прошли в `DemoEconomicsTest`. Денежные векторы из `money-contract-tests.json` пока не подключены к Android-тестам.
- `VehicleFormDraft.kt:37–52` и `CarRecordDialog.kt:122–130` принимают значения выше L, рыночная оценка 0 проходит клиентскую проверку. `CarFormViewModel.kt:132–138` заменяет `amount_out_of_range` общим текстом. Диалоги расходов и преимуществ сохраняют серверный текст ошибки через `CarDetailViewModel.kt:128–132,199–206`. Debug demo не ограничивает суммы групп по текущему денежному контракту. Требуемые исправления — P1-02 и P2-01.

## 7. Фотогалерея

`image_url` представлен как `String?`. Он используется в выборке и Comparison, как требует контракт. `image_urls` отсутствует в `ListingData`, `CarInput`, `Car` и Room. `VehicleGallery` принимает только `imageUrl` и открывает список из debug-индекса для `file:///android_asset/demo/vehicles/…?gallery=…`. В real `ListingPreview` использует `VehiclePhoto`, карточка передаёт одно поле `imageUrl`. Пустой массив и порядок серверной галереи теряются на границе маппинга. `domain/Car.kt`, `data/local/ProfitDatabase.kt:14–39`, `VehicleMedia.kt:70–150,199–244`, `AddCarScreen.kt:285–321`, `CarDetailScreen.kt:211`.

**Воспроизведение на Backend.** Импорт `https://auto.drom.ru/demo/101.html` через локальное API вернул три ссылки. После сохранения через Kotlin `NetworkCarRepository.createCar` `GET /api/cars/{id}` вернул одну. Повторено для Toyota Camry, Honda Accord, Mazda 6, Nissan X-Trail и Toyota RAV4. Контрольная запись с массивом в `POST` вернула три ссылки в прежнем порядке после PATCH города и его очистки. Если в существующей форме явно очистить поле адреса фотографии, текущий PATCH включает `image_url: null`. Согласно контракту и коду `backend/app/api/cars.py:88–101` это очищает целиком серверную галерею. Форму следует сделать однозначной для правки характеристики, основного снимка и явного удаления набора.

**Доставка JPG.** Все 15 JPG из Android debug assets побайтно совпали с `web/public/demo/vehicles`. Основное фото Camry по пути `/demo/vehicles/camry-xv50-silver.jpg` вернуло 404 с Backend на 8000. Отдельный временный HTTP-сервер на `127.0.0.1:5174` с корнем `web/public` отдал все 15 JPG со статусом 200 и `image/jpeg`. `loadApiBitmap` требует абсолютный URL того же origin, что API, и сразу отвергает относительный путь и внешний origin. Для demo-real можно использовать отдельный debug base URL для строго заданного префикса маршрута либо явно сопоставлять этот префикс проверенным локальным JPG из debug assets. Вариант с Web-хостом позволяет использовать именно те изображения, которые доступны Web. Сбой кадра должен оставлять остальные кадры и навигацию доступными. Использовать оригинальные фотографии Дрома в этом прогоне не требовалось.

## 8. Сетевой режим, хранение и ошибки

| Область | Фактическое поведение |
| --- | --- |
| Debug demo | Пустой `dromProfitApiBaseUrl` выбирает `DemoCarRepository`. `dromProfitDemoScenario` поддерживает `empty` по умолчанию, `seeded`, `showcase` и `list-error`. В проекте сохранён совместимый `dromProfitDemoEmpty`. |
| Debug real | `-PdromProfitApiBaseUrl=http://10.0.2.2:8000/` подставляет URL в `BuildConfig` и выбирает Retrofit. Это параметр сборки. Для USB после `adb reverse` подходит `http://127.0.0.1:8000/`. |
| Release | Фабрика всегда выбирает real. Пустой адрес или HTTP даёт ошибку конфигурации, рабочий вариант — заранее заданный HTTPS. Нет fallback на demo. |
| Токен и 401 | Создаёт лениво с `Mutex`, сохраняет атомарно в приватном каталоге без backup. Токен передаётся через Bearer. При 401 помечает текущую сессию отклонённой, смена происходит отдельным действием. JVM + API подтвердил повторное использование токена, 404 для чужого ID и ручную смену после 401. |
| Room и выбор сравнения | Сетевое зеркало изолировано по хешам endpoint и Bearer. Выбор сравнения в SharedPreferences привязан к endpoint, при успешной загрузке фильтруется по доступным ID, явная смена сессии очищает его. При отсутствии сети реальный репозиторий отдаёт ошибку и не читает старый снимок из Room. Персистентность на Android остаётся в acceptance. |
| Таймауты и сбои | В Retrofit используются значения OkHttp по умолчанию, повторы соединения и redirect отключены. `IOException` передаётся как `network_error` для таймаутов и отсутствия сети. Для фото таймауты подключения и чтения 4 и 5 секунд, размер до 8 МиБ, ограничение декодированных пикселей. Поведение реального устройства при отключении сети не измерялось. |

Файлы `di/NetworkRepositoryFactory.kt:17–39`, `data/NetworkCarRepository.kt:48–61,175–230`, `data/PrivateTextFile.kt`, `data/local/NetworkCarMirror.kt`, `data/local/SelectionIdsStore.kt`, `presentation/viewmodels/SelectionViewModel.kt`.

## 9. Запуск и подключение к Backend

Docker Desktop первоначально не работал. После запуска Docker CLI/Compose v2.40.3 создали отдельный проект `drom-mvp-android-audit-871` из интеграционного `backend/compose.yaml`. Использован `DROM_LISTINGS_SOURCE=demo` и `DROM_LISTINGS_EXTRA_FIXTURE=/srv/shared/fixtures/demo-showcase.json`. API опубликован только на `127.0.0.1:8000`, PostgreSQL 17.6 внутри сети проекта, миграция `0003_car_gallery` подтверждена SQL. `GET /openapi.json` вернул версию 0.3.0. После прогона проект остановлен через `docker compose down` без удаления тома. `drom-mvp-android-audit-871_postgres_data` остался. Запросы с URL объявлений отправлялись как JSON на loopback; сетевых запросов к Дрому не выполнялось.

Временный Kotlin/JVM harness использовал оригинальные `ProfitApi`, `ContractJson` и `NetworkCarRepository`, Retrofit с локальным origin и отдельный временный токен. Получены все 18 успешных HTTP-ответов, выполнены импорт пяти машин, правка, сохранение, карточки, полный CRUD расходов, преимуществ и заметок, сброс и установка флагов, Summary, Comparison, ручной ввод, удаление, проверка повторного открытия сессии и изоляции. Отдельно подтверждены ошибка demo `404.html`, отказ денежного контракта и контрольное сохранение галереи. Kotlin-код совпадает с сетевой реализацией приложения. Activity, Room, Android-сетевой стек разрешений и UI в этом прогоне не запускались.

APK на данном хосте не собран. Поэтому маршруты `10.0.2.2`, `adb reverse`, рендер UI, вращение экрана, переключение кадров и восстановление после перезапуска APK остаются открыты. Приёмка Android проводится в новой анонимной сессии на той же серверной реализации, что у Web. Межплатформенная синхронизация не входит в MVP.

## 10. Интерфейс и безопасность

По коду доступны выборка, импорт, предпросмотр, ручной ввод, карточка с секциями расходов, преимуществ и заметок, встроенный Summary и отдельный Comparison. `Theme.kt`, `Color.kt`, `SiteChrome.kt` и общие компоненты задают последовательное оформление. README указывает на Web-коммит `986d4d3` как визуальный референс. Отдельного согласованного артефакта Android UI Kit в доступных worktree не обнаружено. Соответствие такому артефакту и блокирующие визуальные дефекты на устройстве проверить не удалось.

- Явное основное разрешение — `INTERNET`. Activity экспортируется как launcher. Main manifest запрещает cleartext, release требует HTTPS, глобального обхода TLS в исходниках не обнаружено. Debug network security сейчас разрешает cleartext всем origin, ограничение нужно сузить до требуемых dev-адресов. Итоговый merged manifest проверяется после сборки.
- Токен хранится приватно и вне backup. Локальная база исключена из backup и device transfer. В исходниках нет сетевого логгера запросов с Bearer и явного вывода токенов. Прямой загрузчик фото не добавляет заголовок авторизации. Проверка логов на устройстве остаётся открытой.
- Проверены пути обоих Android-коммитов и типовые текстовые признаки приватных ключей и токенов. Совпадений не найдено. В обязательной конфигурации отсутствуют персональные локальные пути. В Git нет `local.properties`, ключей подписи, APK/AAB или build-вывода. Wrapper JAR — часть сборки, есть синтетические JSON-фикстуры и 15 debug-фото с атрибуцией. Прямых вызовов к реальным отчётам не обнаружено.
- `DataStore Preferences`, `MapLibre Compose` и `Play Services Location` объявлены в Gradle, вызовы их API в Android-исходниках отсутствуют. Проверить влияние их транзитивных зависимостей на merged manifest и размер APK можно после восстановления SDK. Пять `.idea` файлов присутствуют, в `deploymentTargetSelector.xml` нет идентификатора устройства или домашнего пути.

## 11. Дефекты и блокеры

| ID · приоритет | Доказательство и условия воспроизведения | Исправление следующего этапа |
| --- | --- | --- |
| **P0-01 · потеря фото при сохранении импорта** | На поднятом Backend во всех пяти карточках `101–105` сохранено по одному кадру при трёх в ответе импорта. `data/ContractJson.kt:21–28,64–83,117–145`, `domain/Car.kt:34–73`, `presentation/forms/VehicleFormDraft.kt:57–89`. | Провести упорядоченный `List<String>` из `ListingData` через редактируемый черновик в `CarInput` и `POST`. Сохранить согласованность `image_url` с первым кадром и передавать пустой массив для отсутствующей галереи. Добавить тест 3 → 3. |
| **P0-ENV · сборка и запуск не подтверждены** | `assembleDebug`, `testDebugUnitTest` и `lintDebug` возвращают `SDK location not found`, ожидаемый APK отсутствует. `android/app/build.gradle.kts:36` задаёт API 37. `adb` и AVD не найдены. Это блокер окружения. | Подключить установленный Android SDK API 37 и Build Tools 36.0.0 через `ANDROID_HOME` или игнорируемый `local.properties`. Повторить Wrapper-задачи и инструментальный прогон на устройстве. |
| **P1-01 · серверные галереи и фото не видны в real** | `GET CarDetail` успешно возвращает массив, `readCar` его не читает. `VehicleMedia.kt:93–150,199–244` принимает один адрес и локальный debug-индекс. Относительный JPG не проходит `toHttpUrlOrNull`. Backend 8000 дал 404, `web/public` на 5174 — 15 ответов 200. `CarDetailScreen.kt:211`, `AddCarScreen.kt:307`. | Прочитать и передавать массив в detail и preview, сохранить его через Room или вернуть серверный detail без усечения. Список и Comparison используют `image_url`. Разрешать demo-пути через отдельный debug origin либо проверенный asset resolver. Сохранить порядок и независимые заглушки. Явное удаление галереи отделить от редактирования характеристик. |
| **P1-02 · клиентская денежная валидация и текст ошибки** | Цена L+1 и оценка 0 проходят `VehicleFormDraft.kt:37–52`, фактор L+1 проходит `CarRecordDialog.kt:122–130`. Backend отклоняет. `CarFormViewModel.kt:132–138` скрывает код `amount_out_of_range` под общим сообщением. `DemoCarRepository.kt:153–184,306–307` не контролирует L и агрегаты. | Применить 1…L для цены и известной оценки, 0…L для суммы фактора, сохранить nullable ввод. Показать серверную ошибку суммы/агрегата с сохранением введённых данных. Подключить денежные векторы для сетевого режима и debug demo. |
| **P1-03 · слишком широкое cleartext-разрешение в debug** | `android/app/src/debug/AndroidManifest.xml` и `res/xml/debug_network_security.xml:3` разрешают HTTP через `base-config` для всех origin. | Оставить запрет по умолчанию, разрешить HTTP только согласованным адресам dev в соответствующей сборке. Проверить эмулятор и USB `adb reverse`. Выпускной HTTPS и стандартная проверка TLS сохраняются. |
| **P2-01 · производные деньги расходятся с Kotlin-типами контракта** | `domain/Comparison.kt:10–13,25` и `ContractJson.kt:170–190` используют `BigInteger`. JVM подтверждает точность в диапазоне API. `NetworkCarRepositoryTest.kt:138–164` проверяет суммы за `Long.MAX_VALUE` вне диапазона 0.3.0. | Перейти на `Long` / `Long?` для производных money-полей и заменить тест на пределы L, 2L, 1−L и 3L−1. Сохранять точный вывод и знак. |
| **P2-02 · полные PATCH вложенных записей** | `NetworkCarRepository.kt:112–149` использует один JSON для POST и PATCH. При правке одной записи отправляются её остальные значения. `CarDetailViewModel.kt:88–112` пропускает только полностью неизменённый ввод. Если значение изменилось между открытием и сохранением, устаревший черновик может его перезаписать. | Сравнивать с исходной записью и передавать только изменённые поля. Явный null оставлять для осознанной очистки nullable суммы, комментария или ключа. Проверить сохранение непереданных полей. |
| **P2-03 · файлы и зависимости сборки** | `android/.idea/`, проектный `gradlew` имеет mode 100644, `app/build.gradle.kts:50` выдаёт deprecated warning. DataStore, MapLibre и Play Location объявлены без вызовов. | Сохранить необходимые командные настройки IDE, остальные пересмотреть в Android PR. Для Linux до правки использовать `bash ./gradlew`. Удалить неиспользуемые зависимости после проверки merged manifest. Перейти на актуальное объявление каталога assets. |

## 12. Будущий merge и план интеграции

`git merge-tree --trivial-merge c83ef78 integration/mvp 2bc1ea8` завершился с кодом 0. Текстовых конфликтов в выводе нет. У интеграции в `android/` изменён только `android/AGENTS.md`, которого нет в диффе Android. Сейчас общих конфликтующих путей нет. Семантические точки пересечения — OpenAPI 0.3.0, общие fixtures через `../shared/fixtures`, доставка 15 JPG и инструкции запуска. Android DTO из старой ветки сверять с интеграционной спецификацией. Версии общих файлов из интеграции должны сохраниться.

1. В следующем этапе создать отдельную review-ветку и worktree от актуального `integration/mvp`. Зафиксировать точный Android SHA, пересчитать merge-base и `merge-tree` перед объединением, выбрать один из двух refs с одинаковым SHA. Перенести Gradle-проект, код, ресурсы и тесты Android; использование пяти IDE-файлов решить при ревью.
2. После разрешения на изменение кода выполнить merge закреплённого Android-коммита в review-ветке и проверить список изменений относительно исходной интеграции. Ожидаются изменения в `android/` и согласованные документы. Обновления `backend/`, `web/`, `shared/openapi.yaml`, fixtures, корневого `AGENTS.md` и `android/AGENTS.md` требуют отдельной причины и командного согласования. Сохранить интеграционные версии этих файлов.
3. Для P0-01 и P1-01 обновить `ListingData`, `CarInput`, представление `CarDetail`, JSON-маппинг, форму и сохранение. Модели кратких ответов `Car` используют основной снимок. Для Room выбрать миграцию с сохранением debug-выборок, если добавляется колонка, или исключить усечение при возврате detail. Проверить пустую галерею, 1 и 3 снимка, порядок, ошибку одного снимка и сохранение массива после правки характеристик.
4. Согласовать dev origin фото и разрешённые адреса HTTP. Завершить P1-02 и P1-03. Зафиксировать `Long` / `Long?` денежного контракта и точный PATCH. Подключить общие расчётные и денежные векторы. Для API базовый адрес содержит origin без повторного `/api`.
5. Прогнать `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest`, проверить сборку release с согласованным HTTPS. На устройстве пройти первый запуск, перезапуск, 401, недоступность API, восстановление выбора, real Demo Provider `101–105` и ручной ввод. Проверить пустые и неполные группы, CRUD, отрицательный E, разницы, ошибки пределов и галереи. Сравнить серверные результаты Android и Web для одинакового ввода. Для проверки отсутствия регрессий использовать существующие проверки Backend и Web из runbook.
6. До PR из `integration/mvp` в `main` требуется закрыть P0 и блокирующие P1, получить воспроизводимый APK, подтверждённый результат сквозной приёмки и совместный review Core, Android и продукта. Обновить статусы INT-06/08/12/17/18 по результатам. Сетевой импорт и семантика рыночной оценки остаются отдельными согласованиями. Справочник `factor_key` остаётся за пределами MVP.

## 13. Команды для повторения

Подготовить Android SDK `platforms;android-37`, `build-tools;36.0.0`, `platform-tools`, JDK 21 и устройство либо эмулятор с API не ниже 26. Установку SDK и доступных AVD выполнять штатным SDK Manager. [Документация sdkmanager](https://developer.android.com/tools/sdkmanager)

PowerShell, изолированный Backend. Для повторного аудита используйте новое имя проекта и отдельный локальный пароль. `POSTGRES_PASSWORD` передаётся через окружение или игнорируемый `backend/.env`.

```powershell
Set-Location 'C:\DromProfit\Drom-Profit-integration'
$env:DROM_LISTINGS_SOURCE = 'demo'
$env:DROM_LISTINGS_EXTRA_FIXTURE = '/srv/shared/fixtures/demo-showcase.json'
docker compose -p drom-mvp-android-acceptance -f backend/compose.yaml up --build -d --wait api-loopback
(Invoke-RestMethod 'http://127.0.0.1:8000/openapi.json').info.version
```

При варианте с отдельным dev origin для фото запустить из интеграционного worktree временный локальный сервер статических файлов либо Vite по runbook. URL для эмулятора `http://10.0.2.2:5174` используется только после реализации параметра фото. Физическому устройству по USB нужен отдельный `adb reverse tcp:5174 tcp:5174` и `http://127.0.0.1:5174`.

```powershell
python -m http.server 5174 --bind 127.0.0.1 --directory web/public
```

PowerShell, Android. Для демонстрации без API можно собрать с `-PdromProfitDemoScenario=showcase` и без параметра API. Ожидаемый APK для обеих debug-конфигураций лежит по одному пути.

```powershell
Set-Location 'C:\DromProfit\Drom-Profit-android\android'
.\gradlew.bat :app:tasks --all
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug '-PdromProfitApiBaseUrl=http://10.0.2.2:8000/'
adb devices -l
adb install -r 'app\build\outputs\apk\debug\app-debug.apk'
adb shell am start -n 'ru.dedmos.dromprofit/.presentation.MainActivity'
.\gradlew.bat :app:connectedDebugAndroidTest '-PdromProfitApiBaseUrl=http://10.0.2.2:8000/'
```

Для физического устройства выбрать его через `adb -s DEVICE_SERIAL`, выполнить `adb reverse tcp:8000 tcp:8000` и собрать debug с `-PdromProfitApiBaseUrl=http://127.0.0.1:8000/`. Для release использовать `:app:assembleRelease` с согласованным HTTPS endpoint и принятыми командой параметрами подписи. После проверки остановить только свой Compose-проект командой `docker compose -p drom-mvp-android-acceptance -f backend/compose.yaml down`. PostgreSQL остаётся внутри сети Compose, том сохраняется.

## 14. Ограничения и итоговое состояние Git

Приёмка APK, instrumented tests, merged manifest, визуальная сверка Android UI Kit, сетевые проверки через эмулятор и физическое устройство остаются открыты. Воспроизведение Android HTTP проведено на уровне оригинального Kotlin-репозитория на JVM. Для Android нужен локальный SDK и устройство. Для общего релиза также остаются письменный доступ к реальному источнику, подтверждение трактовки рыночной оценки и согласованные регрессии. Рабочая БД пользователей не использовалась.

Исходные ветки и функциональные файлы обоих worktree сохранены. Выполнен только анализ merge. Commit, push, rebase и cherry-pick не выполнялись. Единственный новый файл для будущего отслеживания — этот отчёт, он оставлен вне индекса.

```text
C:\DromProfit\Drom-Profit-android       2bc1ea8
## HEAD (no branch)

C:\DromProfit\Drom-Profit-integration   7190cf2
## integration/mvp...origin/integration/mvp
?? docs/ANDROID_AUDIT.md
```
