package ru.dedmos.dromprofit.data

import ru.dedmos.dromprofit.domain.AssessmentStatus
import ru.dedmos.dromprofit.domain.Car
import ru.dedmos.dromprofit.domain.Comparison
import ru.dedmos.dromprofit.domain.ComparisonItem
import ru.dedmos.dromprofit.domain.ComparisonWarning
import ru.dedmos.dromprofit.domain.ExpenseKind
import ru.dedmos.dromprofit.domain.Summary
import ru.dedmos.dromprofit.domain.MaxMoney
import ru.dedmos.dromprofit.domain.repository.RepositoryException

// Debug-only mock of the server responses for offline demonstration.
internal fun demoSummary(car: Car): Summary {
    if (car.price !in 1L..MaxMoney) {
        demoAmountOutOfRange("Цена должна быть от 1 до 1 000 000 000 000 ₽.")
    }
    val totalExpenses = demoGroupTotal(car.expenses.map { it.amount })
    val totalBenefits = demoGroupTotal(car.benefits.map { it.amount })
    val unknownExpenses = car.expenses.count { it.amount == null }
    val unknownBenefits = car.benefits.count { it.amount == null }
    val complete = car.expensesReviewed && car.benefitsReviewed &&
        unknownExpenses == 0 && unknownBenefits == 0
    val acquisitionCost = car.price + totalExpenses
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

internal fun demoGroupTotal(amounts: Iterable<Long?>): Long = amounts.fold(0L) { total, amount ->
    if (amount == null) return@fold total
    if (amount !in 0L..(MaxMoney - total)) {
        demoAmountOutOfRange("Сумма расходов или преимуществ не может превышать 1 000 000 000 000 ₽.")
    }
    total + amount
}

internal fun demoAmountOutOfRange(message: String): Nothing =
    throw RepositoryException("amount_out_of_range", message)

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
        if (minimum < 0L) {
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
