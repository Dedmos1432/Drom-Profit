package ru.dedmos.dromprofit.domain

import java.math.BigInteger

enum class AssessmentStatus { COMPLETE, INCOMPLETE }

data class Summary(
    val carId: String,
    val listingPrice: Long,
    val totalExpenses: BigInteger,
    val totalBenefits: BigInteger,
    val acquisitionCost: BigInteger,
    val adjustedCost: BigInteger,
    val assessmentStatus: AssessmentStatus,
    val unknownExpenses: Int,
    val unknownBenefits: Int
)

data class Comparison(
    val items: List<ComparisonItem>,
    val warnings: List<ComparisonWarning>,
    val provisional: Boolean
)

data class ComparisonItem(val summary: Summary, val differenceFromMin: BigInteger?)

data class ComparisonWarning(
    val code: String,
    val message: String,
    val factorKey: String? = null
)
