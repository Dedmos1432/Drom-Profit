package ru.dedmos.dromprofit.data

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.dedmos.dromprofit.domain.AssessmentStatus
import ru.dedmos.dromprofit.domain.Benefit
import ru.dedmos.dromprofit.domain.CarInput
import ru.dedmos.dromprofit.domain.CarSource
import ru.dedmos.dromprofit.domain.Expense
import ru.dedmos.dromprofit.domain.ExpenseKind
import ru.dedmos.dromprofit.domain.MaxMoney
import ru.dedmos.dromprofit.domain.repository.RepositoryException
import ru.dedmos.dromprofit.presentation.forms.CarField
import ru.dedmos.dromprofit.presentation.forms.asRubles
import ru.dedmos.dromprofit.presentation.forms.validationErrors
import java.io.File

class DemoContractTest {
    @Test
    fun inputFormsRespectSharedMoneyVectors() {
        val vectors = fixture("money-contract-tests.json").asJsonObject
        assertEquals(vectors["limit"].asLong, MaxMoney)
        for (test in vectors.objects("inputs")) {
            val name = test.requiredString("id")
            val literal = test.stringOrNull("literal")
            val entered = literal.takeUnless { it == "null" }.orEmpty()
            val expected = test.getAsJsonObject("expect")
            val draft = MutableList(CarField.entries.size) { "" }.apply {
                this[CarField.BRAND.ordinal] = "Toyota"
                this[CarField.MODEL.ordinal] = "Camry"
                this[CarField.PRICE.ordinal] = entered
                this[CarField.MARKET_PRICE.ordinal] = entered
            }
            val errors = draft.validationErrors(null)
            assertEquals(name, expected["price"].isJsonNull, CarField.PRICE !in errors)
            assertEquals(name, expected["market_valuation"].isJsonNull, CarField.MARKET_PRICE !in errors)
            val amountValid = literal == "null" || entered.asRubles()?.let { it in 0L..MaxMoney } == true
            assertEquals(name, expected["amount"].isJsonNull, amountValid)
        }
        val json = CarInput(CarSource.MANUAL, "Toyota", "Camry", MaxMoney, marketPrice = MaxMoney).toJson()
        assertEquals(MaxMoney.toString(), json["price"].toString())
        assertEquals(MaxMoney.toString(), json["market_valuation"].toString())
    }

    @Test
    fun checksAggregateBeforeOverflowAndMatchesDerivativeBounds() {
        val vectors = fixture("money-contract-tests.json").asJsonObject
        val aggregates = vectors.getAsJsonObject("aggregates")
        val first = aggregates["first"].asLong
        val remainder = aggregates["remainder"].asLong
        val overflow = aggregates["overflow"].asLong
        assertEquals(MaxMoney, demoGroupTotal(listOf(first, null, remainder, 0L)))
        val rejected = runCatching { demoGroupTotal(listOf(first, remainder, overflow)) }.exceptionOrNull()
        assertEquals(aggregates["reject_code"].asString, (rejected as RepositoryException).code)

        val bounds = vectors.getAsJsonObject("derivatives")
        val max = CarInput(CarSource.MANUAL, "A", "Max", bounds["max_price"].asLong).toCar(
            id = "max", expensesReviewed = true, benefitsReviewed = true,
            expenses = listOf(Expense("expense", "ТО", ExpenseKind.MAINTENANCE, bounds["max_expenses"].asLong)),
        )
        val min = CarInput(CarSource.MANUAL, "B", "Min", bounds["min_price"].asLong).toCar(
            id = "min", expensesReviewed = true, benefitsReviewed = true,
            benefits = listOf(Benefit("benefit", "Оснащение", bounds["max_benefits"].asLong)),
        )
        val comparison = demoCompare(listOf(max, min))
        assertEquals(bounds["acquisition_cost"].asLong, comparison.items[0].summary.acquisitionCost)
        assertEquals(bounds["adjusted_max"].asLong, comparison.items[0].summary.adjustedCost)
        assertEquals(bounds["adjusted_min"].asLong, comparison.items[1].summary.adjustedCost)
        assertEquals(bounds["difference_max"].asLong, comparison.items[0].differenceFromMin)
        assertTrue(comparison.warnings.any { it.code == "negative_adjusted_cost" })
    }

    @Test
    fun sharedShowcasePreservesPhotosAndAllFiveEconomicResults() {
        val listings = fixture("demo-showcase.json").asJsonArray.map { it.asJsonObject }
        val assessments = fixture("demo-showcase-assessments.json").asJsonObject
        val cars = demoShowcaseCars(listings, assessments)
        assertEquals(listOf("101", "102", "103", "104", "105"), cars.map { it.listingId })
        assertEquals(listOf(1_184_000L, 1_455_000L, 1_453_000L, 1_495_000L, 1_850_000L),
            cars.map { demoSummary(it).adjustedCost })
        assertEquals(listOf(29_000L, 130_000L, 15_000L, 0L, 0L), cars.map { demoSummary(it).totalExpenses })
        assertEquals(listOf(45_000L, 25_000L, 12_000L, 55_000L, 0L), cars.map { demoSummary(it).totalBenefits })
        assertEquals(listOf(true, true, false, true, false), cars.map { demoSummary(it).assessmentStatus == AssessmentStatus.COMPLETE })
        assertEquals(listOf(0, 0, 1, 0, 0), cars.map { demoSummary(it).unknownExpenses })
        assertTrue(cars.all { it.marketPrice == null && it.imageUrls.size == 3 && it.imageUrl == it.imageUrls.first() })
        assertEquals(listings.map { row -> row.getAsJsonArray("image_urls").map { it.asString } }, cars.map { it.imageUrls })
    }

    private fun fixture(name: String): JsonElement {
        val directory = requireNotNull(System.getProperty("dromProfitSharedFixtures"))
        return File(directory, name).reader(Charsets.UTF_8).use { JsonParser.parseReader(it) }
    }
}
