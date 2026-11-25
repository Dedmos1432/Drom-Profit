package ru.dedmos.dromprofit.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.dedmos.dromprofit.domain.Benefit
import ru.dedmos.dromprofit.domain.BenefitInput
import ru.dedmos.dromprofit.domain.Car
import ru.dedmos.dromprofit.domain.usecases.detail.CarDetailUseCases
import ru.dedmos.dromprofit.domain.Expense
import ru.dedmos.dromprofit.domain.ExpenseInput
import ru.dedmos.dromprofit.domain.ExpenseKind
import ru.dedmos.dromprofit.domain.Note
import ru.dedmos.dromprofit.domain.NoteInput
import ru.dedmos.dromprofit.domain.NoteKind
import ru.dedmos.dromprofit.domain.repository.RepositoryException
import ru.dedmos.dromprofit.domain.Summary

internal sealed interface DetailDialog {
    data object DeleteCar : DetailDialog
    data class EditExpense(
        val expense: Expense? = null,
        val kind: ExpenseKind = ExpenseKind.REPAIR,
    ) : DetailDialog
    data class EditBenefit(val benefit: Benefit? = null) : DetailDialog
    data class EditNote(val note: Note? = null, val kind: NoteKind = NoteKind.OTHER) : DetailDialog
    data class DeleteExpense(val expense: Expense) : DetailDialog
    data class DeleteBenefit(val benefit: Benefit) : DetailDialog
    data class DeleteNote(val note: Note) : DetailDialog
}

internal data class CarDetailUiState(
    val car: Car? = null,
    val summary: Summary? = null,
    val loading: Boolean = true,
    val summaryLoading: Boolean = true,
    val loadError: String? = null,
    val summaryError: String? = null,
    val busy: Boolean = false,
    val mutationError: String? = null,
    val dialog: DetailDialog? = null,
    val deleted: Boolean = false,
) {
    val actionsEnabled: Boolean
        get() = !loading && !summaryLoading && !busy && loadError == null
}

@HiltViewModel
class CarDetailViewModel @Inject constructor(
    private val useCases: CarDetailUseCases,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CarDetailUiState())
    internal val state = mutableState.asStateFlow()
    private var carId: String? = null

    internal fun load(id: String) {
        val current = state.value
        if (current.busy || current.deleted || current.dialog != null) return
        if (carId != null && (current.loading || current.summaryLoading)) return
        if (carId != id) mutableState.value = CarDetailUiState()
        carId = id
        mutableState.update { it.copy(loading = true, loadError = null, mutationError = null) }
        viewModelScope.launch { readCard(id) }
    }

    internal fun retrySummary() {
        val id = carId ?: return
        if (state.value.loading || state.value.summaryLoading || state.value.busy) return
        mutableState.update { it.copy(summaryLoading = true, summaryError = null) }
        viewModelScope.launch { readSummary(id) }
    }

    internal fun showDialog(dialog: DetailDialog?) {
        if (dialog != null && !state.value.actionsEnabled) return
        if (state.value.busy) return
        mutableState.update { it.copy(dialog = dialog, mutationError = null) }
    }

    internal fun reviewExpenses(checked: Boolean) = mutate { id ->
        reviewExpenses(id, checked)
    }

    internal fun reviewBenefits(checked: Boolean) = mutate { id ->
        reviewBenefits(id, checked)
    }

    internal fun saveExpense(input: ExpenseInput, id: String?) = mutate { carId ->
        saveExpense(carId, input, id)
    }

    internal fun saveBenefit(input: BenefitInput, id: String?) = mutate { carId ->
        saveBenefit(carId, input, id)
    }

    internal fun saveNote(input: NoteInput, id: String?) = mutate { carId ->
        saveNote(carId, input, id)
    }

    internal fun confirmDelete() {
        when (val dialog = state.value.dialog) {
            DetailDialog.DeleteCar -> mutate(deletesCar = true) { id -> deleteCar(id) }
            is DetailDialog.DeleteExpense -> mutate { id -> deleteExpense(id, dialog.expense.id) }
            is DetailDialog.DeleteBenefit -> mutate { id -> deleteBenefit(id, dialog.benefit.id) }
            is DetailDialog.DeleteNote -> mutate { id -> deleteNote(id, dialog.note.id) }
            else -> Unit
        }
    }

    private fun mutate(deletesCar: Boolean = false, action: suspend CarDetailUseCases.(String) -> Unit) {
        val id = carId ?: return
        if (!state.value.actionsEnabled) return
        mutableState.update { it.copy(busy = true, mutationError = null) }
        viewModelScope.launch {
            val result = detailResult { useCases.action(id) }
            if (result.isFailure) {
                val message = result.exceptionOrNull().detailMessage("Не удалось сохранить изменение.")
                mutableState.update { it.copy(busy = false, mutationError = message) }
                return@launch
            }
            mutableState.update { it.copy(dialog = null, deleted = deletesCar) }
            if (!deletesCar) readCard(id)
            mutableState.update { it.copy(busy = false) }
        }
    }

    private suspend fun readCard(id: String) {
        mutableState.update {
            it.copy(loading = true, summaryLoading = true, loadError = null, summaryError = null, summary = null)
        }
        val result = detailResult { useCases.getCar(id) }
        val car = result.getOrNull()
        mutableState.update {
            it.copy(
                car = car ?: it.car,
                loading = false,
                loadError = result.exceptionOrNull()?.detailMessage("Не удалось загрузить автомобиль."),
                summaryLoading = car != null,
            )
        }
        if (car != null) readSummary(id)
    }

    private suspend fun readSummary(id: String) {
        val result = detailResult { useCases.getSummary(id) }
        mutableState.update {
            it.copy(
                summary = result.getOrNull(),
                summaryLoading = false,
                summaryError = result.exceptionOrNull()?.detailMessage("Не удалось загрузить расчёт."),
            )
        }
    }
}

private inline fun <T> detailResult(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (exception: CancellationException) {
    throw exception
} catch (exception: Exception) {
    Result.failure(exception)
}

private fun Throwable?.detailMessage(default: String): String = when (this) {
    is RepositoryException -> if (code == "unauthorized") {
        "Сессия недоступна. Вернитесь к выборке, чтобы начать новую."
    } else {
        message.takeIf(String::isNotBlank) ?: default
    }
    else -> default
}
