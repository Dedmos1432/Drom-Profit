package ru.dedmos.dromprofit.domain

enum class AssessmentStatus { COMPLETE, INCOMPLETE }

data class Summary(
    val carId: String,
    val listingPrice: Long,
    val totalExpenses: Long,
    val totalBenefits: Long,
    val acquisitionCost: Long,
    val adjustedCost: Long,
    val assessmentStatus: AssessmentStatus,
    val unknownExpenses: Int,
    val unknownBenefits: Int
)

data class Comparison(
    val items: List<ComparisonItem>,
    val warnings: List<ComparisonWarning>,
    val provisional: Boolean
)

data class ComparisonItem(val summary: Summary, val differenceFromMin: Long?)

data class ComparisonWarning(
    val code: String,
    val message: String,
    val factorKey: String? = null
)
