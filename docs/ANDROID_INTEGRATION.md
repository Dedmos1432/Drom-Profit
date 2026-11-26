# Дром Профит — стабилизация Android

Этап 8.7.3 · 26 ноября 2026 года · `review/android-integration`

**Функциональные исправления проверены на JVM и локальном Backend 0.3.0.** Пять автомобилей сохранили по три кадра в порядке импорта, денежные результаты совпали с общей витриной. Успешны 15 JVM-тестов Android, все 18 REST-операций, 445 тестов Backend, 111 тестов Web, typecheck, lint и production build Web. Полная приёмка Android открыта. На хосте отсутствует Android SDK, APK и запуск на устройстве не подтверждены.

## 1. Git и область работ

Рабочая директория `C:\DromProfit\Drom-Profit-android-integration`. До правок чистая ветка `review/android-integration` была на `5f0b841f71e6f4432825193623df6e1b7d935e0c`. `git merge-base --is-ancestor 2bc1ea8 HEAD` вернул 0. В `integration/mvp` на `3452734` сохраняется исходный [аудит](ANDROID_AUDIT.md), `origin/main` на `c83ef78`. Сравнение с `integration/mvp` для Backend, Web, shared и AGENTS.md пустое. Изменения этапа оставлены в рабочем дереве review-ветки. OpenAPI, PostgreSQL-миграции, Backend, Web и исторический аудит совпадают с состоянием на начало этапа.

## 2. Статусы замечаний аудита

| ID | Результат этапа | Оставшаяся проверка |
| --- | --- | --- |
| P0-01 | Исправлен. `image_urls` проходит через предпросмотр, форму, POST и детальное чтение. Все пять импортов сохраняют 3 из 3 кадров. | Прогон пользовательского сценария в APK. |
| P1-01 | Исправлен для локального demo. Preview и detail используют порядок массива. Локальный resolver сопоставляет путь с JPG в debug, тест отличает известный путь от отсутствующего и произвольного. | Реальный рендер и переключение на устройстве, инструментальный тест ошибки кадра, проверка обновления Room. |
| P1-02 | Исправлен. Формы ограничивают денежные значения. Real сохраняет черновики при 422. Debug отклоняет превышение суммы группы без записи и сброса подтверждения. | Визуальная проверка сообщений и черновиков APK. |
| P1-03 | Исправлен в конфигурации. Debug HTTP разрешён для `10.0.2.2` и `127.0.0.1`, release принимает HTTPS. | Debug/release merged manifests, `NetworkSecurityPolicy`, маршруты эмулятора и USB. |
| P2-01 | Исправлен. Производные денежные поля переведены на `Long` / `Long?`, тесты используют границы 0.3.0. | Проверка отображения крупных и отрицательных значений в APK. |
| P2-02 | Исправлен. PATCH строится по отличиям исходной записи и черновика. Проверены явный null, сохранение чужого изменения непереданного поля и независимые флаги. | Выполнить тот же сценарий через экран карточки. |
| P0-ENV | Открыт. Gradle находит модуль и задачи. SDK отсутствует, все задачи сборки и Android-проверок заблокированы. | Подключить SDK Platform 37, Build Tools 36.0.0 и устройство либо AVD. |
| P2-03 | Оставлен в бэклоге. Сохранены версии, зависимости и IDE-конфигурация. Gradle выводит существующее предупреждение о `assets.srcDir`. | Разбор технического долга после приёмки. |

## 3. Галерея, OpenAPI и Room

`ListingData` и детальное представление `Car` содержат упорядоченный `List<String>`. У `CarInput` необязательный список позволяет сохранить старые запросы с одним `image_url`. `ContractJson` читает новый массив, сохраняет порядок, снимает повторы и ограничивает вход до 64 элементов. Старый ответ с основным фото даёт один кадр, отсутствие фото даёт пустой список. Выборка и Comparison используют основную ссылку. Предпросмотр и карточка отображают весь набор.

В форме правка города или другой характеристики оставляет оба поля фотографий вне PATCH. Изменение основного снимка переносит его в начало и сохраняет порядок остальных. Очистка применяется после явного действия «Очистить галерею» и сохранения формы. Удаление текста из поля адреса само по себе сохраняет исходную галерею. Ответы `Car` после записи остаются краткими, детальная карточка снова читает `CarDetail`. Операции и поля общего API сверены с [OpenAPI 0.3.0](../shared/openapi.yaml) и [контрактом галереи](PHOTO_GALLERY_CONTRACT.md).

Room 1 → 2 добавляет nullable-колонку с JSON-массивом. Старые записи сохраняются, до получения полного массива используется сохранённое основное фото. Когда краткий ответ содержит ту же обложку, зеркало сохраняет ранее прочитанный массив. При другой обложке следующее чтение detail получает актуальный порядок с сервера. Схема зарегистрирована без destructive migration. Добавлен инструментальный тест с существующей базой версии 1, краткими ответами, явной очисткой и повторным открытием. Он ожидает запуска Android.

`/demo/vehicles/<имя>.jpg` обрабатывается только в debug. Разрешены точный префикс, имя без вложенных каталогов, кодирования и query, и наличие JPG в каталоге assets. Старые локальные debug-записи `file:///android_asset/demo/vehicles/…` загружаются только по точному префиксу и из тех же комплектных JPG. Ссылки в сетевых моделях и запросах остаются серверными. Атрибуции находятся рядом с изображениями. У каждого кадра свой placeholder, выбор остальных доступен. Добавлен Compose-тест с отсутствующим первым кадром. Загрузка абсолютных фотографий по-прежнему ограничена origin API и политикой загрузчика. Права и доставка оригинальных снимков Дрома не проверялись.

Debug Showcase строится из `shared/fixtures/demo-showcase.json` и `shared/fixtures/demo-showcase-assessments.json`. Новая автономная выборка использует согласованные характеристики и оценки. Ранее сохранённые локальные выборки и пользовательские правки сохраняются при миграции. Для real App создаёт пустую анонимную сессию, импорт и сохранение остаются отдельными действиями.

## 4. Деньги и PATCH

Все денежные свойства, включая R, B, C, E и `difference_from_min`, представлены `Long` / `Long?`. Форма автомобиля принимает цену и указанную оценку 1…L. Сумма записи 0…L либо null, где L = 1 000 000 000 000 ₽. Парсер формы отклоняет дроби и значения вне диапазона, сериализатор отправляет десятичные целочисленные JSON-литералы. Нулевая и неизвестная суммы различаются. Проверены C = 2L, E = 1−L, максимальная разница 3L−1, предварительная оценка и null-разницы при двойном учёте.

Backend рассчитывает итоги и окончательно проверяет диапазоны в real. Для автономной витрины debug вычисляет итоги на локальных данных по тем же формулам. Перед записью debug проверяет сумму изменённой группы с остатком до лимита, поэтому переполнение отклоняется до изменения строки и флагов. В форме машины есть отдельное сообщение `amount_out_of_range`, а диалог денежной записи сохраняет ввод и показывает ошибку группы.

Карточка передаёт с диалогом исходный снимок записи. Сетевой репозиторий отправляет только ключи, которые изменились, или сразу возвращает исходную запись при пустом PATCH. Явный null применяется к сумме, комментарию и `factor_key`. Отдельные прогоны проверили правку названия и типа, комментарий и ключ, 0 → null, null → 0, сохранение серверного комментария при изменении другого поля, сброс только изменённой группы и независимость денежных флагов от заметок. [Денежный контракт](MONEY_CONTRACT.md)

## 5. Сеть и безопасность

Параметр сборки `dromProfitApiBaseUrl` сохранён. Debug без адреса запускает demo, debug с адресом использует Retrofit, release работает с явно заданным HTTPS. В debug-манифесте подключён network security config с запретом cleartext по умолчанию и двумя разрешёнными адресами без поддоменов. Сетевая фабрика дополнительно проверяет их для HTTP. Фабрика оставляет стандартную проверку TLS, отключает redirects и повтор соединения. Bearer хранится в приватном `AtomicFile` вне резервной копии, приложение не включает логгер заголовков. Compose опубликовал только API на loopback. PostgreSQL оставался внутри сети проекта.

## 6. Пять автомобилей на реальном Backend

Прогон запустил Backend из текущего worktree в отдельном Compose-проекте `drom-android-873`, PostgreSQL 17.6, миграция `0003_car_gallery`. Сетевые вызовы Kotlin шли только на `127.0.0.1:8000`. Demo Provider получил локальные fixtures. Следующие итоги прочитаны из серверного Summary после подготовки оценок общими fixtures через публичные операции API.

| Автомобиль | P | R | B | C | E | Статус | Кадров |
| --- | ---: | ---: | ---: | ---: | ---: | --- | ---: |
| Toyota Camry | 1 200 000 | 29 000 | 45 000 | 1 229 000 | 1 184 000 | complete | 3 |
| Honda Accord | 1 350 000 | 130 000 | 25 000 | 1 480 000 | 1 455 000 | complete | 3 |
| Mazda 6 | 1 450 000 | 15 000 | 12 000 | 1 465 000 | 1 453 000 | incomplete, 1 неизвестный расход | 3 |
| Nissan X-Trail | 1 550 000 | 0 | 55 000 | 1 550 000 | 1 495 000 | complete, подтверждена пустая группа R | 3 |
| Toyota RAV4 | 1 850 000 | 0 | 0 | 1 850 000 | 1 850 000 | incomplete, обе группы открыты | 3 |

У всех пяти `market_valuation = null`. Подтверждены отсутствие записи после простого импорта, редактирование города с сохранением трёх ссылок, изменение основного кадра, явная очистка и случаи одного кадра и пустой галереи. Создавались отдельные ручные карточки для границ рублёвых значений. Проверены Comparison на двух и пяти машинах, 401 с явной сменой токена, 404 для чужого ID и отсутствующего объявления, 422 для лимита и изоляция сессий. После Docker restart Backend новый процесс Kotlin с тем же локальным токеном прочитал прежние пять карточек с тем же порядком снимков и результатами Summary без повторного выпуска токена.

## 7. Результаты проверок

| Проверка | Результат |
| --- | --- |
| JVM-тесты Android | 15 прошли в JUnit. Kotlin CLI 2.3.0 из Gradle Wrapper с `-language-version 2.2 -jvm-target 11` и Temurin 21.0.7. Скомпилированы оригинальные модели, JSON, формы, Retrofit/OkHttp, сетевой и demo-репозитории и тесты. Android/Room заменены временными JVM-заглушками вне Git. Шесть общих расчётных наборов, денежные векторы, порядок и лимит 64, resolver и точный PATCH прошли. |
| Kotlin + Backend | 18 из 18 операций вернули контрактные 200/201/204. Проверены 401/404/422. Изолированный прогон также подтвердил сохранение записей и флагов demo при отклонении POST/PATCH, используя репозиторий и замену Android-хранилища. |
| Backend | `docker compose … --profile test run --rm tests`, 445 passed на отдельной PostgreSQL в Linux Docker. Нагрузочный тест на этом этапе не запускался. |
| Web | `npm.cmd run test -- --maxWorkers=2`, 111 из 111. `npm.cmd run typecheck`, `npm.cmd run lint` и `npm.cmd run build` прошли. Vite/Rollup предупредил о двух позициях аннотаций в зависимости Zod. |
| Gradle задачи | `:app:tasks --all` прошла. Определены debug, release, unit tests, lint и connected tests. |
| Gradle сборка и проверки | `:app:assembleDebug` с `http://10.0.2.2:8000/`, `:app:testDebugUnitTest`, `:app:lintDebug` и `:app:assembleRelease` с тестовым HTTPS дали `SDK location not found` до компиляции или анализа. |
| APK, Android UI и Room | APK не получен, `adb` и AVD не найдены. `connectedDebugAndroidTest`, merged manifests, установка, перезапуск приложения, Android Room, локальная отрисовка и восстановление после отключения сети ожидают Android SDK и устройство. |

JDK 21 доступна через Gradle toolchain, Wrapper 9.4.1, AGP 9.2.1, Kotlin проекта 2.2.10, compileSdk 37, targetSdk 36, minSdk 26. Android SDK Platform 37, Build Tools 36.0.0, `adb` и Android Emulator на проверенных путях отсутствуют. `ANDROID_HOME` и `ANDROID_SDK_ROOT` не заданы, локальный `android/local.properties` отсутствует. Сборка release не создавала ключи или публикацию. Проверки JVM показывают готовность сетевых и доменных сценариев до полной приёмки приложения.

## 8. Локальный стенд и сборка

Установите Android SDK Platform 37, Build Tools 36.0.0 и Platform Tools. Для эмулятора нужен AVD с API ≥ 26. Задайте `ANDROID_HOME` или в игнорируемом `android/local.properties` укажите `sdk.dir`. Для запуска Gradle используйте JDK 21. Положите тестовый пароль изолированного PostgreSQL в окружение или игнорируемый `backend/.env`.

```powershell
Set-Location 'C:\DromProfit\Drom-Profit-android-integration\backend'
$env:DROM_LISTINGS_SOURCE = 'demo'
$env:DROM_LISTINGS_EXTRA_FIXTURE = '/srv/shared/fixtures/demo-showcase.json'
docker compose -p drom-android-acceptance up --build -d --wait api-loopback
(Invoke-RestMethod 'http://127.0.0.1:8000/openapi.json').info.version
```

```powershell
Set-Location 'C:\DromProfit\Drom-Profit-android-integration\android'
.\gradlew.bat :app:assembleDebug '-PdromProfitApiBaseUrl=http://10.0.2.2:8000/'
adb devices -l
adb install -r 'app\build\outputs\apk\debug\app-debug.apk'
adb shell am start -n 'ru.dedmos.dromprofit/.presentation.MainActivity'
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest '-PdromProfitApiBaseUrl=http://10.0.2.2:8000/'
```

Для телефона по USB используйте `adb -s DEVICE_SERIAL reverse tcp:8000 tcp:8000` и соберите с `-PdromProfitApiBaseUrl=http://127.0.0.1:8000/`. Для автономного debug выполните `.\gradlew.bat :app:assembleDebug '-PdromProfitDemoScenario=showcase'` без параметра API. Обе debug-сборки устанавливаются из одного пути APK. Проверка release использует `.\gradlew.bat :app:assembleRelease '-PdromProfitApiBaseUrl=https://example.test/'`, этот endpoint предназначен для компиляции. Для остановки из каталога `backend` выполните `docker compose -p drom-android-acceptance down`, том сохранится. [Общий runbook](INTEGRATION_RUNBOOK.md)

## 9. Бэклог и файлы

INT-06, INT-08, INT-12, INT-17 и INT-18 имеют статус **Partial**. Kotlin + API, денежный контракт, demo-галерея и инструкция запуска подтверждены в описанном объёме. До закрытия остаются SDK, APK, Gradle unit tests/lint и приёмка на устройстве. Сетевой импорт и семантика рыночной оценки сохраняют прежние условия, справочник `factor_key` остаётся за пределами MVP. [Интеграционный бэклог](INTEGRATION_BACKLOG.md)

Итоговый список рабочих файлов. `A` обозначает новый файл вне индекса.

```text
M	android/README.md
M	android/app/build.gradle.kts
M	android/app/src/androidTestDebug/java/ru/dedmos/dromprofit/data/local/DemoCarRoomTest.kt
D	android/app/src/debug/assets/showcase-cars.json
M	android/app/src/debug/java/ru/dedmos/dromprofit/data/DemoCarRepository.kt
M	android/app/src/debug/java/ru/dedmos/dromprofit/data/DemoEconomics.kt
M	android/app/src/debug/res/xml/debug_network_security.xml
M	android/app/src/main/java/ru/dedmos/dromprofit/data/ContractJson.kt
M	android/app/src/main/java/ru/dedmos/dromprofit/data/NetworkCarRepository.kt
M	android/app/src/main/java/ru/dedmos/dromprofit/data/local/CarEntityMapper.kt
M	android/app/src/main/java/ru/dedmos/dromprofit/data/local/NetworkCarMirror.kt
M	android/app/src/main/java/ru/dedmos/dromprofit/data/local/ProfitDatabase.kt
M	android/app/src/main/java/ru/dedmos/dromprofit/di/DataModule.kt
M	android/app/src/main/java/ru/dedmos/dromprofit/di/NetworkRepositoryFactory.kt
M	android/app/src/main/java/ru/dedmos/dromprofit/domain/Car.kt
M	android/app/src/main/java/ru/dedmos/dromprofit/domain/CarListing.kt
M	android/app/src/main/java/ru/dedmos/dromprofit/domain/CarRecords.kt
M	android/app/src/main/java/ru/dedmos/dromprofit/domain/Comparison.kt
M	android/app/src/main/java/ru/dedmos/dromprofit/domain/repository/CarRepository.kt
M	android/app/src/main/java/ru/dedmos/dromprofit/domain/usecases/detail/CarDetailUseCases.kt
M	android/app/src/main/java/ru/dedmos/dromprofit/presentation/components/Components.kt
M	android/app/src/main/java/ru/dedmos/dromprofit/presentation/components/VehicleMedia.kt
M	android/app/src/main/java/ru/dedmos/dromprofit/presentation/forms/VehicleFormDraft.kt
M	android/app/src/main/java/ru/dedmos/dromprofit/presentation/screens/add/AddCarScreen.kt
M	android/app/src/main/java/ru/dedmos/dromprofit/presentation/screens/add/VehicleForm.kt
M	android/app/src/main/java/ru/dedmos/dromprofit/presentation/screens/comparison/ComparisonScreen.kt
M	android/app/src/main/java/ru/dedmos/dromprofit/presentation/screens/detail/CarDetailScreen.kt
M	android/app/src/main/java/ru/dedmos/dromprofit/presentation/screens/detail/CarRecordDialog.kt
M	android/app/src/main/java/ru/dedmos/dromprofit/presentation/screens/detail/CarRecordSections.kt
M	android/app/src/main/java/ru/dedmos/dromprofit/presentation/screens/selection/SelectionScreen.kt
M	android/app/src/main/java/ru/dedmos/dromprofit/presentation/viewmodels/CarDetailViewModel.kt
M	android/app/src/main/java/ru/dedmos/dromprofit/presentation/viewmodels/CarFormViewModel.kt
M	android/app/src/test/java/ru/dedmos/dromprofit/data/NetworkCarRepositoryTest.kt
M	docs/INTEGRATION_BACKLOG.md
M	docs/INTEGRATION_RUNBOOK.md
A	android/app/src/androidTestDebug/java/ru/dedmos/dromprofit/data/DebugNetworkPolicyTest.kt
A	android/app/src/androidTestDebug/java/ru/dedmos/dromprofit/data/local/GalleryRoomMigrationTest.kt
A	android/app/src/androidTestDebug/java/ru/dedmos/dromprofit/presentation/VehicleGalleryTest.kt
A	android/app/src/debug/java/ru/dedmos/dromprofit/data/DemoShowcase.kt
A	android/app/src/main/java/ru/dedmos/dromprofit/data/DemoPhotoPaths.kt
A	android/app/src/main/java/ru/dedmos/dromprofit/domain/CarPhotos.kt
A	android/app/src/main/java/ru/dedmos/dromprofit/domain/Money.kt
A	android/app/src/test/java/ru/dedmos/dromprofit/data/GalleryContractTest.kt
A	android/app/src/testDebug/java/ru/dedmos/dromprofit/data/DemoContractTest.kt
A	docs/ANDROID_INTEGRATION.md
```
