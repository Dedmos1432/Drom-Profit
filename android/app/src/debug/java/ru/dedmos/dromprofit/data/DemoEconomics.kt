package ru.dedmos.dromprofit.data

import ru.dedmos.dromprofit.domain.AssessmentStatus
import ru.dedmos.dromprofit.domain.Car
import ru.dedmos.dromprofit.domain.Comparison
import ru.dedmos.dromprofit.domain.ComparisonItem
import ru.dedmos.dromprofit.domain.ComparisonWarning
import ru.dedmos.dromprofit.domain.ExpenseKind
import ru.dedmos.dromprofit.domain.Summary
import java.math.BigInteger

// Debug-only mock of the server responses for offline demonstration.
internal fun demoSummary(car: Car): Summary {
    val totalExpenses = car.expenses.mapNotNull { it.amount }.sumOf { it.toBigInteger() }
    val totalBenefits = car.benefits.mapNotNull { it.amount }.sumOf { it.toBigInteger() }
    val unknownExpenses = car.expenses.count { it.amount == null }
    val unknownBenefits = car.benefits.count { it.amount == null }
    val complete = car.expensesReviewed && car.benefitsReviewed &&
        unknownExpenses == 0 && unknownBenefits == 0
    val acquisitionCost = car.price.toBigInteger() + totalExpenses
    return Summary(
        carId = car.id,
        listingPrice = car.price,
        totalExpenses = totalExpenses,
        totalBenefits = totalBenefits,
        acquisitionCost = acquisitionCost,
        adjustedCost = acquisitionCost - totalBenefits,
        assessmentStatus = if (complete) AssessmentStatus.COMPLETE else AssessmentStatus.INCOMPLETE,
        unknownExpenses = unknownExpenses,
        unknownBenefits = unknownBenefits
    )
}

internal fun demoCompare(cars: List<Car>): Comparison {
    val summaries = cars.map(::demoSummary)
    val minimum = summaries.minOf { it.adjustedCost }
    val expenses = cars.flatMap { it.expenses }
        .filter { it.kind == ExpenseKind.MISSING_EQUIPMENT }
        .mapNotNull { it.factorKey }.toSet()
    val benefits = cars.flatMap { it.benefits }.mapNotNull { it.factorKey }.toSet()
    val conflicts = expenses.intersect(benefits)
    val warnings = buildList {
        for (factorKey in conflicts.sorted()) {
            add(ComparisonWarning("double_count", "Учтите оснащение одним способом в выбранных автомобилях", factorKey))
        }
        if (summaries.any { it.assessmentStatus == AssessmentStatus.INCOMPLETE }) {
            add(ComparisonWarning("incomplete", "Часть оценок ещё не завершена"))
        }
        if (minimum < BigInteger.ZERO) {
            add(ComparisonWarning(
                "negative_adjusted_cost",
                "Скорректированная стоимость отрицательна. Проверьте оценку преимуществ"
            ))
        }
    }
    return Comparison(
        items = summaries.map { summary ->
            val difference = if (conflicts.isEmpty()) summary.adjustedCost - minimum else null
            ComparisonItem(summary, difference)
        },
        warnings = warnings,
        provisional = warnings.isNotEmpty()
    )
}
