package ru.dedmos.dromprofit.domain

data class Car(
    val id: String,
    val sourceUrl: String?,
    val brand: String,
    val model: String,
    val year: Long?,
    val price: Long,
    val mileage: Long?,
    val engineVolume: Double?,
    val transmission: String?,
    val imageUrl: String?,

    val trim: String? = null,
    val engineType: String? = null,
    val enginePower: Long? = null,
    val driveType: String? = null,
    val city: String? = null,
    val marketPrice: Long? = null,
    val source: CarSource = CarSource.MANUAL,
    val listingId: String? = null,
    val fetchedAt: String? = null,
    val expensesReviewed: Boolean = false,
    val benefitsReviewed: Boolean = false,
    val expenses: List<Expense> = emptyList(),
    val benefits: List<Benefit> = emptyList(),
    val notes: List<Note> = emptyList(),
    val imageUrls: List<String> = listOfNotNull(imageUrl)
)

enum class CarSource { DROM, DEMO, MANUAL }

data class ListingData(
    val source: CarSource,
    val sourceUrl: String? = null,
    val listingId: String? = null,
    val fetchedAt: String? = null,
    val brand: String? = null,
    val model: String? = null,
    val price: Long? = null,
    val year: Long? = null,
    val mileage: Long? = null,
    val engineVolume: Double? = null,
    val transmission: String? = null,
    val imageUrl: String? = null,
    val trim: String? = null,
    val engineType: String? = null,
    val enginePower: Long? = null,
    val driveType: String? = null,
    val city: String? = null,
    val marketPrice: Long? = null,
    val imageUrls: List<String> = listOfNotNull(imageUrl)
)

data class CarInput(
    val source: CarSource,
    val brand: String,
    val model: String,
    val price: Long,
    val sourceUrl: String? = null,
    val listingId: String? = null,
    val fetchedAt: String? = null,
    val year: Long? = null,
    val mileage: Long? = null,
    val engineVolume: Double? = null,
    val transmission: String? = null,
    val imageUrl: String? = null,
    val trim: String? = null,
    val engineType: String? = null,
    val enginePower: Long? = null,
    val driveType: String? = null,
    val city: String? = null,
    val marketPrice: Long? = null,
    val imageUrls: List<String>? = null
)
