package ru.dedmos.dromprofit.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import ru.dedmos.dromprofit.domain.AssessmentStatus
import ru.dedmos.dromprofit.domain.Benefit
import ru.dedmos.dromprofit.domain.BenefitInput
import ru.dedmos.dromprofit.domain.Car
import ru.dedmos.dromprofit.domain.CarInput
import ru.dedmos.dromprofit.domain.Comparison
import ru.dedmos.dromprofit.domain.ComparisonItem
import ru.dedmos.dromprofit.domain.ComparisonWarning
import ru.dedmos.dromprofit.domain.Expense
import ru.dedmos.dromprofit.domain.ExpenseInput
import ru.dedmos.dromprofit.domain.ListingData
import ru.dedmos.dromprofit.domain.Note
import ru.dedmos.dromprofit.domain.NoteInput
import ru.dedmos.dromprofit.domain.Summary
import ru.dedmos.dromprofit.domain.toListingData

internal fun ListingData.toCarInput(
    brand: String = requireNotNull(this.brand),
    model: String = requireNotNull(this.model),
    price: Long = requireNotNull(this.price)
) = CarInput(
    source, brand, model, price, sourceUrl, listingId, fetchedAt, year, mileage,
    engineVolume, transmission, imageUrl, trim, engineType, enginePower, driveType, city, marketPrice
)

internal fun CarInput.toCar(
    id: String,
    expensesReviewed: Boolean = false,
    benefitsReviewed: Boolean = false,
    expenses: List<Expense> = emptyList(),
    benefits: List<Benefit> = emptyList(),
    notes: List<Note> = emptyList()
) = Car(
    id = id,
    sourceUrl = sourceUrl,
    brand = brand,
    model = model,
    year = year,
    price = price,
    mileage = mileage,
    engineVolume = engineVolume,
    transmission = transmission,
    imageUrl = imageUrl,
    trim = trim,
    engineType = engineType,
    enginePower = enginePower,
    driveType = driveType,
    city = city,
    marketPrice = marketPrice,
    source = source,
    listingId = listingId,
    fetchedAt = fetchedAt,
    expensesReviewed = expensesReviewed,
    benefitsReviewed = benefitsReviewed,
    expenses = expenses,
    benefits = benefits,
    notes = notes
)

internal fun CarInput.toJson(): JsonObject = JsonObject().apply {
    addProperty("source", source.name.lowercase())
    addProperty("listing_id", listingId)
    addProperty("brand", brand)
    addProperty("model", model)
    addProperty("price", price)
    addProperty("year", year)
    addProperty("mileage_km", mileage)
    addProperty("engine_volume_l", engineVolume)
    addProperty("transmission", transmission)
    addProperty("image_url", imageUrl)
    addProperty("trim", trim)
    addProperty("fuel_type", engineType)
    addProperty("power_hp", enginePower)
    addProperty("drive_type", driveType)
    addProperty("city", city)
    addProperty("listing_url", sourceUrl)
    addProperty("market_valuation", marketPrice)
    addProperty("fetched_at", fetchedAt)
}

internal fun CarInput.changesFrom(original: Car): JsonObject {
    val previous = original.toListingData().toCarInput().toJson()
    return toJson().apply {
        remove("source")
        remove("listing_id")
        remove("fetched_at")
        for (name in keySet().toList()) {
            if (get(name) == previous.get(name)) remove(name)
        }
    }
}

internal fun ExpenseInput.toJson(): JsonObject = JsonObject().apply {
    addProperty("title", title)
    addProperty("kind", kind.name.lowercase())
    addProperty("amount", amount)
    addProperty("factor_key", factorKey)
    addProperty("comment", comment)
}

internal fun BenefitInput.toJson(): JsonObject = JsonObject().apply {
    addProperty("title", title)
    addProperty("amount", amount)
    addProperty("factor_key", factorKey)
    addProperty("comment", comment)
}

internal fun NoteInput.toJson(): JsonObject = JsonObject().apply {
    addProperty("kind", kind.name.lowercase())
    addProperty("text", text)
}

internal fun JsonObject.readListing() = ListingData(
    source = requiredEnum("source"),
    sourceUrl = stringOrNull("listing_url"),
    listingId = stringOrNull("listing_id"),
    fetchedAt = stringOrNull("fetched_at"),
    brand = stringOrNull("brand"),
    model = stringOrNull("model"),
    price = longOrNull("price"),
    year = longOrNull("year"),
    mileage = longOrNull("mileage_km"),
    engineVolume = optional("engine_volume_l")?.let { it.numberText().toDouble().also { value -> require(value.isFinite()) } },
    transmission = stringOrNull("transmission"),
    imageUrl = stringOrNull("image_url"),
    trim = stringOrNull("trim"),
    engineType = stringOrNull("fuel_type"),
    enginePower = longOrNull("power_hp"),
    driveType = stringOrNull("drive_type"),
    city = stringOrNull("city"),
    marketPrice = longOrNull("market_valuation")
)

internal fun JsonObject.readCar() = readListing().toCarInput().toCar(
    id = requiredString("id"),
    expensesReviewed = requiredBoolean("expenses_reviewed"),
    benefitsReviewed = requiredBoolean("benefits_reviewed"),
    expenses = objects("expenses").map(JsonObject::readExpense),
    benefits = objects("benefits").map(JsonObject::readBenefit),
    notes = objects("notes").map(JsonObject::readNote)
)

internal fun JsonObject.readExpense() = Expense(
    id = requiredString("id"),
    title = requiredString("title"),
    kind = requiredEnum("kind"),
    amount = longOrNull("amount"),
    factorKey = stringOrNull("factor_key"),
    comment = stringOrNull("comment")
)

internal fun JsonObject.readBenefit() = Benefit(
    id = requiredString("id"),
    title = requiredString("title"),
    amount = longOrNull("amount"),
    factorKey = stringOrNull("factor_key"),
    comment = stringOrNull("comment")
)

internal fun JsonObject.readNote() = Note(
    id = requiredString("id"),
    kind = requiredEnum("kind"),
    text = requiredString("text")
)

internal fun JsonObject.readSummary() = Summary(
    carId = requiredString("car_id"),
    listingPrice = required("listing_price").numberText().toLong(),
    totalExpenses = required("total_expenses").numberText().toBigInteger(),
    totalBenefits = required("total_benefits").numberText().toBigInteger(),
    acquisitionCost = required("acquisition_cost").numberText().toBigInteger(),
    adjustedCost = required("adjusted_cost").numberText().toBigInteger(),
    assessmentStatus = requiredEnum<AssessmentStatus>("assessment_status"),
    unknownExpenses = required("unknown_expenses").numberText().toInt(),
    unknownBenefits = required("unknown_benefits").numberText().toInt()
)

internal fun JsonObject.readComparison() = Comparison(
    items = objects("items").map { item ->
        ComparisonItem(
            summary = item.readSummary(),
            differenceFromMin = item.optional("difference_from_min")?.numberText()?.toBigInteger()
        )
    },
    warnings = objects("warnings").map { warning ->
        ComparisonWarning(
            code = warning.requiredString("code"),
            message = warning.requiredString("message"),
            factorKey = warning.stringOrNull("factor_key")
        )
    },
    provisional = requiredBoolean("provisional")
)

internal fun JsonObject.objects(name: String): List<JsonObject> =
    required(name).asJsonArray.map(JsonElement::getAsJsonObject)

internal fun JsonObject.requiredString(name: String): String = required(name).let {
    require(it.asJsonPrimitive.isString)
    it.asString
}

internal fun JsonObject.stringOrNull(name: String): String? = optional(name)?.let {
    require(it.asJsonPrimitive.isString)
    it.asString
}

internal fun JsonObject.requiredBoolean(name: String): Boolean = required(name).let {
    require(it.asJsonPrimitive.isBoolean)
    it.asBoolean
}

private fun JsonObject.longOrNull(name: String): Long? = optional(name)?.numberText()?.toLong()

private fun JsonObject.required(name: String): JsonElement =
    requireNotNull(optional(name)) { "Missing field: " + name }

private fun JsonObject.optional(name: String): JsonElement? = get(name)?.takeUnless(JsonElement::isJsonNull)

private fun JsonElement.numberText(): String {
    require(asJsonPrimitive.isNumber)
    return asString
}

private inline fun <reified T : Enum<T>> JsonObject.requiredEnum(name: String): T =
    enumValueOf(requiredString(name).uppercase())
