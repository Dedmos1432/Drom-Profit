package ru.dedmos.dromprofit.presentation.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.dedmos.dromprofit.domain.Car
import ru.dedmos.dromprofit.domain.usecases.selection.LoadSelectionUseCase
import ru.dedmos.dromprofit.domain.usecases.session.RestartSessionUseCase
import javax.inject.Inject

@HiltViewModel
internal class SelectionViewModel @Inject constructor(
    private val loadSelection: LoadSelectionUseCase,
    private val restartSession: RestartSessionUseCase,
    private val savedState: SavedStateHandle
) : ViewModel() {
    val isDemo: Boolean get() = loadSelection.isDemo

    private val screenState = MutableStateFlow<ScreenState<List<Car>>>(ScreenState.Loading)
    val state = screenState.asStateFlow()
    val selectedIds = savedState.getStateFlow(SelectedIdsKey, arrayListOf<String>())
    private var request: Job? = null

    fun refresh() {
        load(resetSession = false)
    }

    fun restart() {
        load(resetSession = true)
    }

    fun toggleSelected(carId: String) {
        val cars = (screenState.value as? ScreenState.Content)?.data ?: return
        if (cars.none { it.id == carId }) return
        val ids = ArrayList(selectedIds.value)
        if (!ids.remove(carId)) ids.add(carId)
        savedState[SelectedIdsKey] = ids
    }

    private fun load(resetSession: Boolean) {
        if (request?.isActive == true) return
        request = viewModelScope.launch {
            screenState.value = ScreenState.Loading
            if (resetSession) {
                try {
                    restartSession()
                    savedState[SelectedIdsKey] = arrayListOf<String>()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    screenState.value = ScreenState.Error(
                        message = "Сессия недоступна",
                        sessionExpired = true,
                        restartError = error.screenError("Не удалось начать новую сессию.").message
                    )
                    return@launch
                }
            }
            try {
                val cars = loadSelection()
                val availableIds = cars.mapTo(mutableSetOf()) { it.id }
                savedState[SelectedIdsKey] = ArrayList(selectedIds.value.filter { it in availableIds })
                screenState.value = ScreenState.Content(cars)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                screenState.value = error.screenError("Не удалось загрузить выборку.")
            }
        }
    }

    private companion object {
        const val SelectedIdsKey = "selectedCarIds"
    }
}
