package ru.dedmos.dromprofit.data

import com.google.gson.JsonObject
import ru.dedmos.dromprofit.domain.Car

internal fun demoShowcaseCars(listings: List<JsonObject>, assessments: JsonObject): List<Car> =
    listings.map { listing ->
        val id = listing.requiredString("listing_id")
        val assessment = assessments.getAsJsonObject(id)
        listing.readListing().toCarInput().toCar(
            id = "showcase-$id",
            expensesReviewed = assessment.requiredBoolean("expenses_reviewed"),
            benefitsReviewed = assessment.requiredBoolean("benefits_reviewed"),
            expenses = assessment.objects("expenses").mapIndexed { index, value ->
                value.withDemoId("$id-expense-$index").readExpense()
            },
            benefits = assessment.objects("benefits").mapIndexed { index, value ->
                value.withDemoId("$id-benefit-$index").readBenefit()
            },
            notes = assessment.objects("notes").mapIndexed { index, value ->
                value.withDemoId("$id-note-$index").readNote()
            },
        )
    }

private fun JsonObject.withDemoId(id: String): JsonObject = deepCopy().apply { addProperty("id", id) }
