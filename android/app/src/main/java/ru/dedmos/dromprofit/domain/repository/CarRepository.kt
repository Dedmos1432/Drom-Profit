package ru.dedmos.dromprofit.domain.repository

import ru.dedmos.dromprofit.domain.Benefit
import ru.dedmos.dromprofit.domain.BenefitInput
import ru.dedmos.dromprofit.domain.Car
import ru.dedmos.dromprofit.domain.CarInput
import ru.dedmos.dromprofit.domain.Comparison
import ru.dedmos.dromprofit.domain.Expense
import ru.dedmos.dromprofit.domain.ExpenseInput
import ru.dedmos.dromprofit.domain.ListingData
import ru.dedmos.dromprofit.domain.Note
import ru.dedmos.dromprofit.domain.NoteInput
import ru.dedmos.dromprofit.domain.Summary

interface CarRepository {
    val isDemo: Boolean

    suspend fun restartSession()
    suspend fun getCars(): List<Car>
    suspend fun getCar(carId: String): Car
    suspend fun importListing(url: String): ListingData
    suspend fun createCar(input: CarInput): Car
    suspend fun updateCar(original: Car, input: CarInput): Car
    suspend fun updateReviewed(
        carId: String,
        expensesReviewed: Boolean? = null,
        benefitsReviewed: Boolean? = null
    ): Car
    suspend fun deleteCar(carId: String)

    suspend fun saveExpense(carId: String, input: ExpenseInput, expenseId: String? = null): Expense
    suspend fun deleteExpense(carId: String, expenseId: String)
    suspend fun saveBenefit(carId: String, input: BenefitInput, benefitId: String? = null): Benefit
    suspend fun deleteBenefit(carId: String, benefitId: String)
    suspend fun saveNote(carId: String, input: NoteInput, noteId: String? = null): Note
    suspend fun deleteNote(carId: String, noteId: String)

    suspend fun getSummary(carId: String): Summary
    suspend fun compare(carIds: List<String>): Comparison
}

class RepositoryException(
    val code: String,
    override val message: String,
    cause: Throwable? = null
) : Exception(message, cause)
