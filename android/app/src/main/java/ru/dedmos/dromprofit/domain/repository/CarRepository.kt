package ru.dedmos.dromprofit.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
    val requiresNewSession: Flow<Boolean> get() = flowOf(false)

    suspend fun restartSession()
    suspend fun getCars(): List<Car>
    suspend fun loadDemoCars(): List<Car> =
        throw RepositoryException("demo_unavailable", "Демо-автомобили доступны в демонстрационном режиме.")
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

    suspend fun saveExpense(carId: String, input: ExpenseInput, original: Expense? = null): Expense
    suspend fun deleteExpense(carId: String, expenseId: String)
    suspend fun saveBenefit(carId: String, input: BenefitInput, original: Benefit? = null): Benefit
    suspend fun deleteBenefit(carId: String, benefitId: String)
    suspend fun saveNote(carId: String, input: NoteInput, original: Note? = null): Note
    suspend fun deleteNote(carId: String, noteId: String)

    suspend fun getSummary(carId: String): Summary
    suspend fun compare(carIds: List<String>): Comparison
}

class RepositoryException(
    val code: String,
    override val message: String,
    cause: Throwable? = null
) : Exception(message, cause)
