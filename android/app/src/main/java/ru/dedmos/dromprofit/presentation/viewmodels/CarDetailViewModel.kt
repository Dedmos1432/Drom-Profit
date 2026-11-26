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
import ru.dedmos.dromprofit.domain.Note
import ru.dedmos.dromprofit.domain.NoteInput
import ru.dedmos.dromprofit.domain.repository.RepositoryException
import ru.dedmos.dromprofit.domain.Summary

internal sealed interface DetailDialog {
    data class EditExpense(val expense: Expense? = null) : DetailDialog
    data class EditBenefit(val benefit: Benefit? = null) : DetailDialog
    data class EditNote(val note: Note? = null) : DetailDialog
    data class DeleteExpense(val expense: Expense) : DetailDialog
    data class DeleteBenefit(val benefit: Benefit) : DetailDialog
    data class DeleteNote(val note: Note) : DetailDialog
}

internal enum class ReviewGroup { EXPENSES, BENEFITS }

internal data class CarDetailUiState(
    val car: Car? = null,
    val summary: Summary? = null,
    val loading: Boolean = true,
    val summaryLoading: Boolean = true,
    val loadError: String? = null,
    val summaryError: String? = null,
    val busy: Boolean = false,
    val mutationError: String? = null,
    val failedReview: ReviewGroup? = null,
    val dialog: DetailDialog? = null,
    val notFound: Boolean = false,
) {
    val actionsEnabled: Boolean
        get() = car != null && !loading && !busy && loadError == null
}

@HiltViewModel
class CarDetailViewModel @Inject constructor(
    private val useCases: CarDetailUseCases,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CarDetailUiState())
    internal val state = mutableState.asStateFlow()
    private var carId: String? = null
    private var summaryRequest = 0L

    internal fun load(id: String) {
        val current = state.value
        if (current.busy || current.dialog != null) return
        if (carId != null && current.loading) return
        if (carId != id) mutableState.value = CarDetailUiState()
        carId = id
        mutableState.update { it.copy(loading = true, mutationError = null, failedReview = null) }
        viewModelScope.launch { readCard(id) }
    }

    internal fun retrySummary() {
        val id = carId ?: return
        if (state.value.car == null || state.value.loading || state.value.summaryLoading || state.value.busy) return
        mutableState.update { it.copy(summaryLoading = true, summaryError = null) }
        viewModelScope.launch { readSummary(id) }
    }

    internal fun showDialog(dialog: DetailDialog?) {
        if (dialog != null && !state.value.actionsEnabled) return
        if (state.value.busy) return
        mutableState.update { it.copy(dialog = dialog, mutationError = null, failedReview = null) }
    }

    internal fun reviewExpenses(checked: Boolean) = mutate(reviewGroup = ReviewGroup.EXPENSES) { id ->
        reviewExpenses(id, checked)
    }

    internal fun reviewBenefits(checked: Boolean) = mutate(reviewGroup = ReviewGroup.BENEFITS) { id ->
        reviewBenefits(id, checked)
    }

    internal fun saveExpense(input: ExpenseInput, original: Expense?) =
        mutate { carId -> saveExpense(carId, input, original) }

    internal fun saveBenefit(input: BenefitInput, original: Benefit?) =
        mutate { carId -> saveBenefit(carId, input, original) }

    internal fun saveNote(input: NoteInput, original: Note?) =
        mutate(refreshSummary = false) { carId -> saveNote(carId, input, original) }

    internal fun confirmDelete() {
        when (val dialog = state.value.dialog) {
            is DetailDialog.DeleteExpense -> mutate { id -> deleteExpense(id, dialog.expense.id) }
            is DetailDialog.DeleteBenefit -> mutate { id -> deleteBenefit(id, dialog.benefit.id) }
            is DetailDialog.DeleteNote -> mutate(refreshSummary = false) { id -> deleteNote(id, dialog.note.id) }
            else -> Unit
        }
    }

    private fun mutate(
        refreshSummary: Boolean = true,
        reviewGroup: ReviewGroup? = null,
        action: suspend CarDetailUseCases.(String) -> Unit,
    ) {
        val id = carId ?: return
        if (!state.value.actionsEnabled) return
        mutableState.update { it.copy(busy = true, mutationError = null, failedReview = null) }
        viewModelScope.launch {
            val result = detailResult { useCases.action(id) }
            if (result.isFailure) {
                val message = result.exceptionOrNull().detailMessage("Не удалось сохранить изменение.")
                mutableState.update { it.copy(busy = false, mutationError = message, failedReview = reviewGroup) }
                return@launch
            }
            mutableState.update { it.copy(dialog = null) }
            readCard(id, refreshSummary)
            mutableState.update { it.copy(busy = false) }
        }
    }

    private suspend fun readCard(id: String, refreshSummary: Boolean = true) {
        if (refreshSummary) summaryRequest++
        mutableState.update {
            it.copy(
                loading = true,
                loadError = null,
                notFound = false,
                summaryLoading = refreshSummary || it.summaryLoading,
                summaryError = if (refreshSummary) null else it.summaryError,
            )
        }
        val result = detailResult { useCases.getCar(id) }
        val car = result.getOrNull()
        val failure = result.exceptionOrNull()
        val code = (failure as? RepositoryException)?.code
        val missing = code == "not_found" || code == "http_404"
        mutableState.update {
            it.copy(
                car = if (missing) null else car ?: it.car,
                loading = false,
                loadError = failure?.detailMessage("Не удалось загрузить автомобиль."),
                notFound = missing,
                summary = if (missing) null else it.summary,
                summaryLoading = !missing && (if (refreshSummary) car != null else it.summaryLoading),
            )
        }
        if (refreshSummary && car != null) readSummary(id)
    }

    private suspend fun readSummary(id: String) {
        val request = ++summaryRequest
        val result = detailResult { useCases.getSummary(id) }
        if (request != summaryRequest || state.value.car?.id != id) return
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
