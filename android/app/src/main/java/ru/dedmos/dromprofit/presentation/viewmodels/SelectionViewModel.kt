package ru.dedmos.dromprofit.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.dedmos.dromprofit.domain.Car
import ru.dedmos.dromprofit.domain.Summary
import ru.dedmos.dromprofit.domain.usecases.comparison.RememberComparisonUseCase
import ru.dedmos.dromprofit.domain.usecases.detail.CarDetailUseCases
import ru.dedmos.dromprofit.domain.usecases.selection.LoadSelectionUseCase
import ru.dedmos.dromprofit.domain.usecases.session.RestartSessionUseCase
import javax.inject.Inject

internal data class DeleteCarState(val car: Car, val pending: Boolean = false, val error: String? = null)

@HiltViewModel
internal class SelectionViewModel @Inject constructor(
    private val loadSelection: LoadSelectionUseCase,
    private val restartSession: RestartSessionUseCase,
    private val carUseCases: CarDetailUseCases,
    private val rememberComparison: RememberComparisonUseCase,
) : ViewModel() {
    val isDemo: Boolean get() = loadSelection.isDemo
    val requiresNewSession = loadSelection.requiresNewSession

    private val screenState = MutableStateFlow<ScreenState<List<Car>>>(ScreenState.Loading)
    val state = screenState.asStateFlow()
    private val summaryState = MutableStateFlow<Map<String, ScreenState<Summary>>>(emptyMap())
    val summaries = summaryState.asStateFlow()
    private val selected = MutableStateFlow(rememberComparison.read())
    val selectedIds = selected.asStateFlow()
    private val deletion = MutableStateFlow<DeleteCarState?>(null)
    val deleting = deletion.asStateFlow()

    private var request: Job? = null
    private val summaryRequests = mutableMapOf<String, Job>()

    fun refresh() = load(resetSession = false)

    fun restart() = load(resetSession = true)

    fun retrySummary(carId: String) {
        if (summaryState.value[carId] is ScreenState.Error) {
            summaryState.update { it - carId }
            loadSummary(carId)
        }
    }

    fun loadSummary(carId: String) {
        val content = screenState.value as? ScreenState.Content ?: return
        if (content.data.none { it.id == carId } || summaryState.value.containsKey(carId)) return
        summaryState.update { it + (carId to ScreenState.Loading) }
        summaryRequests[carId] = viewModelScope.launch {
            val result = try {
                ScreenState.Content(loadSelection.summary(carId))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                error.screenError("Не удалось загрузить оценку.")
            }
            summaryState.update { it + (carId to result) }
        }
    }

    fun toggleSelected(carId: String) {
        val cars = (screenState.value as? ScreenState.Content)?.data ?: return
        if (cars.none { it.id == carId }) return
        if (carId in selected.value) exclude(carId)
        else setSelection(selected.value + carId)
    }

    fun selectAll() {
        val cars = (screenState.value as? ScreenState.Content)?.data ?: return
        setSelection(cars.map(Car::id))
    }

    fun clearSelection() = setSelection(emptyList())

    fun exclude(carId: String) = setSelection(selected.value - carId)

    fun pruneSelection(availableIds: Set<String>) = setSelection(selected.value.filter { it in availableIds })

    fun askToDelete(car: Car) {
        if (deletion.value?.pending != true) deletion.value = DeleteCarState(car)
    }

    fun cancelDelete() {
        if (deletion.value?.pending != true) deletion.value = null
    }

    fun confirmDelete() {
        val current = deletion.value ?: return
        if (current.pending || request?.isActive == true) return
        val id = current.car.id
        deletion.value = current.copy(pending = true, error = null)
        viewModelScope.launch {
            try {
                carUseCases.deleteCar(id)
                exclude(id)
                summaryRequests.remove(id)?.cancel()
                summaryState.update { it - id }
                screenState.update { state ->
                    if (state is ScreenState.Content) ScreenState.Content(state.data.filterNot { it.id == id }) else state
                }
                deletion.value = null
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val message = error.screenError("Не удалось удалить автомобиль.").message
                deletion.value = current.copy(pending = false, error = message)
            }
        }
    }

    private fun setSelection(ids: List<String>) {
        if (selected.value == ids) return
        selected.value = ids
        rememberComparison.write(ids)
    }

    private fun load(resetSession: Boolean) {
        if (request?.isActive == true || deletion.value?.pending == true) return
        request = viewModelScope.launch {
            screenState.value = ScreenState.Loading
            summaryRequests.values.forEach { it.cancel() }
            summaryRequests.clear()
            summaryState.value = emptyMap()
            if (resetSession) {
                try {
                    restartSession()
                    clearSelection()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    screenState.value = ScreenState.Error(
                        message = "Сессия больше недоступна",
                        sessionExpired = true,
                        restartError = error.screenError("Не удалось начать новую сессию.").message,
                    )
                    return@launch
                }
            }
            try {
                val cars = loadSelection()
                pruneSelection(cars.mapTo(mutableSetOf(), Car::id))
                screenState.value = ScreenState.Content(cars)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                screenState.value = error.screenError("Не удалось загрузить выборку.")
            }
        }
    }
}
