package ru.dedmos.dromprofit.domain.usecases.detail

import ru.dedmos.dromprofit.domain.Benefit
import ru.dedmos.dromprofit.domain.BenefitInput
import ru.dedmos.dromprofit.domain.Car
import ru.dedmos.dromprofit.domain.Expense
import ru.dedmos.dromprofit.domain.ExpenseInput
import ru.dedmos.dromprofit.domain.Note
import ru.dedmos.dromprofit.domain.NoteInput
import ru.dedmos.dromprofit.domain.Summary
import ru.dedmos.dromprofit.domain.repository.CarRepository

class CarDetailUseCases(private val repository: CarRepository) {
    suspend fun getCar(carId: String): Car = repository.getCar(carId)

    suspend fun getSummary(carId: String): Summary = repository.getSummary(carId)

    suspend fun deleteCar(carId: String) = repository.deleteCar(carId)

    suspend fun reviewExpenses(carId: String, reviewed: Boolean): Car =
        repository.updateReviewed(carId, expensesReviewed = reviewed)

    suspend fun reviewBenefits(carId: String, reviewed: Boolean): Car =
        repository.updateReviewed(carId, benefitsReviewed = reviewed)

    suspend fun saveExpense(carId: String, input: ExpenseInput, expenseId: String?): Expense =
        repository.saveExpense(carId, input, expenseId)

    suspend fun deleteExpense(carId: String, expenseId: String) = repository.deleteExpense(carId, expenseId)

    suspend fun saveBenefit(carId: String, input: BenefitInput, benefitId: String?): Benefit =
        repository.saveBenefit(carId, input, benefitId)

    suspend fun deleteBenefit(carId: String, benefitId: String) = repository.deleteBenefit(carId, benefitId)

    suspend fun saveNote(carId: String, input: NoteInput, noteId: String?): Note =
        repository.saveNote(carId, input, noteId)

    suspend fun deleteNote(carId: String, noteId: String) = repository.deleteNote(carId, noteId)
}
