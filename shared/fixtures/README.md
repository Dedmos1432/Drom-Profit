# Демонстрационные данные
Все автомобили, цены, URL и идентификаторы синтетические. URL вида `https://auto.drom.ru/demo/1.html` — ключи fixture provider, не реальные объявления и не предназначены для сетевого запроса.
- demo-cars.json — сохраненные карточки, Car[] по контракту.
- import-listings.json — preview, ListingData[] по контракту.
- calculation-tests.json — векторы для unit-тестов backend; cars содержат минимальный набор для калькулятора либо полные карточки, expected.items — ComparisonItem[], expected.warning_codes — ожидаемый набор кодов, без привязки к тексту предупреждений. Сравнивать по car_id. Векторы не являются уже выполненными тестами приложения.
