package ru.dedmos.dromprofit.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.dedmos.dromprofit.domain.Car
import ru.dedmos.dromprofit.domain.usecases.comparison.CompareCarsUseCase
import ru.dedmos.dromprofit.domain.Comparison
import ru.dedmos.dromprofit.domain.usecases.selection.LoadSelectionUseCase
import javax.inject.Inject

internal data class ComparisonContent(
    val comparison: Comparison,
    val carsById: Map<String, Car>
)

@HiltViewModel
internal class ComparisonViewModel @Inject constructor(
    private val loadSelection: LoadSelectionUseCase,
    private val compareCars: CompareCarsUseCase
) : ViewModel() {
    val isDemo: Boolean get() = loadSelection.isDemo

    private val screenState = MutableStateFlow<ScreenState<ComparisonContent>>(ScreenState.Loading)
    val state = screenState.asStateFlow()
    private var request: Job? = null

    fun load(carIds: List<String>) {
        request?.cancel()
        if (carIds.size < 2) {
            screenState.value = ScreenState.Error("Выберите хотя бы два автомобиля.")
            return
        }
        request = viewModelScope.launch {
            screenState.value = ScreenState.Loading
            try {
                val content = coroutineScope {
                    val cars = async { loadSelection() }
                    val comparison = async { compareCars(carIds) }
                    ComparisonContent(comparison.await(), cars.await().associateBy(Car::id))
                }
                screenState.value = ScreenState.Content(content)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                screenState.value = error.screenError("Не удалось загрузить сравнение.")
            }
        }
    }
}
