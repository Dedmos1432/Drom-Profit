package ru.dedmos.dromprofit.presentation.forms

import java.math.BigDecimal
import java.net.URI
import ru.dedmos.dromprofit.domain.CarInput
import ru.dedmos.dromprofit.domain.CarSource
import ru.dedmos.dromprofit.domain.ListingData

internal enum class CarFieldInput { TEXT, INTEGER, DECIMAL, URI }

internal enum class CarField(
    val label: String,
    val input: CarFieldInput = CarFieldInput.TEXT,
    val required: Boolean = false,
) {
    BRAND("Марка", required = true),
    MODEL("Модель", required = true),
    PRICE("Цена, ₽", CarFieldInput.INTEGER, required = true),
    YEAR("Год выпуска", CarFieldInput.INTEGER),
    MILEAGE("Пробег, км", CarFieldInput.INTEGER),
    CITY("Город продажи"),
    TRIM("Комплектация"),
    ENGINE_TYPE("Тип двигателя"),
    ENGINE_VOLUME("Объём двигателя, л", CarFieldInput.DECIMAL),
    ENGINE_POWER("Мощность, л. с.", CarFieldInput.INTEGER),
    TRANSMISSION("Коробка передач"),
    DRIVE("Привод"),
    IMAGE_URL("Адрес фотографии", CarFieldInput.URI),
    LISTING_URL("Ссылка на объявление", CarFieldInput.URI),
    MARKET_PRICE("Рыночная оценка Дрома, ₽", CarFieldInput.INTEGER),
}

private val digits = Regex("[0-9]+")
private val groupedDigits = Regex("[0-9]{1,3}(?:[ \u00A0\u202F][0-9]{3})+")
private val decimalNumber = Regex("(?:[0-9]+(?:[,.][0-9]+)?|[,.][0-9]+)")

internal fun List<String>.validationErrors(initial: ListingData?): Map<CarField, String> = buildMap {
    CarField.entries.forEach { field ->
        val rawValue = this@validationErrors[field.ordinal]
        val value = rawValue.trim()
        val error = when {
            value.isEmpty() && !field.required -> null
            field == CarField.BRAND && value.isEmpty() -> "Укажите марку."
            field == CarField.MODEL && value.isEmpty() -> "Укажите модель."
            field == CarField.PRICE && (value.asInteger() ?: 0) < 1 -> "Укажите цену целым числом больше нуля."
            field == CarField.YEAR && (value.asInteger() ?: 0) < 1886 -> "Укажите год не раньше 1886."
            field.input == CarFieldInput.INTEGER && value.asInteger() == null -> "Укажите целое число не меньше нуля."
            field == CarField.ENGINE_VOLUME && value.asEngineVolume() == null -> "Укажите число не меньше нуля."
            field.input == CarFieldInput.URI && rawValue != initial.initialValue(field) && !value.isAbsoluteUri() ->
                "Укажите полный адрес."
            else -> null
        }
        if (error != null) put(field, error)
    }
}

internal fun List<String>.toCarInput(initial: ListingData?): CarInput {
    fun text(field: CarField): String? {
        val value = this[field.ordinal]
        val original = initial.initialValue(field)
        return if (value == original) original else value.trim().ifEmpty { null }
    }

    fun integer(field: CarField): Long? = text(field)?.trim()?.asInteger()

    return CarInput(
        source = initial?.source ?: CarSource.MANUAL,
        listingId = initial?.listingId,
        fetchedAt = initial?.fetchedAt,
        brand = requireNotNull(text(CarField.BRAND)),
        model = requireNotNull(text(CarField.MODEL)),
        price = requireNotNull(integer(CarField.PRICE)),
        year = integer(CarField.YEAR),
        mileage = integer(CarField.MILEAGE),
        city = text(CarField.CITY),
        trim = text(CarField.TRIM),
        engineType = text(CarField.ENGINE_TYPE),
        engineVolume = if (this[CarField.ENGINE_VOLUME.ordinal] == initial.initialValue(CarField.ENGINE_VOLUME)) {
            initial?.engineVolume
        } else {
            text(CarField.ENGINE_VOLUME)?.asEngineVolume()
        },
        enginePower = integer(CarField.ENGINE_POWER),
        transmission = text(CarField.TRANSMISSION),
        driveType = text(CarField.DRIVE),
        imageUrl = text(CarField.IMAGE_URL),
        sourceUrl = text(CarField.LISTING_URL),
        marketPrice = integer(CarField.MARKET_PRICE),
    )
}

internal fun ListingData?.initialValue(field: CarField): String? {
    if (this == null) return null
    return when (field) {
        CarField.BRAND -> brand
        CarField.MODEL -> model
        CarField.PRICE -> price?.toString()
        CarField.YEAR -> year?.toString()
        CarField.MILEAGE -> mileage?.toString()
        CarField.CITY -> city
        CarField.TRIM -> trim
        CarField.ENGINE_TYPE -> engineType
        CarField.ENGINE_VOLUME -> engineVolume?.let { BigDecimal.valueOf(it).stripTrailingZeros().toPlainString() }
        CarField.ENGINE_POWER -> enginePower?.toString()
        CarField.TRANSMISSION -> transmission
        CarField.DRIVE -> driveType
        CarField.IMAGE_URL -> imageUrl
        CarField.LISTING_URL -> sourceUrl
        CarField.MARKET_PRICE -> marketPrice?.toString()
    }
}

private fun String.asInteger(): Long? = when {
    matches(digits) -> toLongOrNull()
    matches(groupedDigits) -> filter { it in '0'..'9' }.toLongOrNull()
    else -> null
}

private fun String.asEngineVolume(): Double? = takeIf { it.matches(decimalNumber) }
    ?.replace(',', '.')
    ?.toDoubleOrNull()
    ?.takeIf { it.isFinite() }

private fun String.isAbsoluteUri(): Boolean = all { it.code in 0x21..0x7e } &&
    runCatching { URI(this).isAbsolute }.getOrDefault(false)
