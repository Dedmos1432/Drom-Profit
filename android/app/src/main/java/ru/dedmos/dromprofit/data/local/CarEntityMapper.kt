package ru.dedmos.dromprofit.data.local

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import ru.dedmos.dromprofit.data.readBenefit
import ru.dedmos.dromprofit.data.readExpense
import ru.dedmos.dromprofit.data.readNote
import ru.dedmos.dromprofit.data.toJson
import ru.dedmos.dromprofit.domain.BenefitInput
import ru.dedmos.dromprofit.domain.Car
import ru.dedmos.dromprofit.domain.CarSource
import ru.dedmos.dromprofit.domain.ExpenseInput
import ru.dedmos.dromprofit.domain.NoteInput

internal fun Car.toEntity(scope: String, position: Long = 0): CarEntity = CarEntity(
    scope = scope,
    id = id,
    position = position,
    source = source.name,
    brand = brand,
    model = model,
    price = price,
    year = year,
    mileage = mileage,
    trim = trim,
    engineType = engineType,
    engineVolume = engineVolume,
    enginePower = enginePower,
    transmission = transmission,
    driveType = driveType,
    city = city,
    imageUrl = imageUrl,
    sourceUrl = sourceUrl,
    marketPrice = marketPrice,
    listingId = listingId,
    fetchedAt = fetchedAt,
    expensesReviewed = expensesReviewed,
    benefitsReviewed = benefitsReviewed,
    expensesJson = JsonArray().apply {
        expenses.forEach { expense ->
            val input = ExpenseInput(expense.title, expense.kind, expense.amount, expense.factorKey, expense.comment)
            add(input.toJson().apply { addProperty("id", expense.id) })
        }
    }.toString(),
    benefitsJson = JsonArray().apply {
        benefits.forEach { benefit ->
            val input = BenefitInput(benefit.title, benefit.amount, benefit.factorKey, benefit.comment)
            add(input.toJson().apply { addProperty("id", benefit.id) })
        }
    }.toString(),
    notesJson = JsonArray().apply {
        notes.forEach { note ->
            add(NoteInput(note.kind, note.text).toJson().apply { addProperty("id", note.id) })
        }
    }.toString()
)

internal fun CarEntity.toDomain(): Car = Car(
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
    source = CarSource.valueOf(source),
    listingId = listingId,
    fetchedAt = fetchedAt,
    expensesReviewed = expensesReviewed,
    benefitsReviewed = benefitsReviewed,
    expenses = expensesJson.readRecords(JsonObject::readExpense),
    benefits = benefitsJson.readRecords(JsonObject::readBenefit),
    notes = notesJson.readRecords(JsonObject::readNote)
)

private fun <T> String.readRecords(read: (JsonObject) -> T): List<T> =
    JsonParser.parseString(this).asJsonArray.map { read(it.asJsonObject) }
