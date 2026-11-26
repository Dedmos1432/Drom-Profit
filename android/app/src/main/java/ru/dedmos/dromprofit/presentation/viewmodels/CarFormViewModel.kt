package ru.dedmos.dromprofit.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.net.URI
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.dedmos.dromprofit.domain.Car
import ru.dedmos.dromprofit.domain.CarInput
import ru.dedmos.dromprofit.domain.ListingData
import ru.dedmos.dromprofit.domain.repository.RepositoryException
import ru.dedmos.dromprofit.domain.usecases.vehicleform.VehicleFormUseCase

internal data class ImportPreview(val requestedUrl: String, val listing: ListingData, val version: Int)

internal data class CarFormUiState(
    val original: Car? = null,
    val preview: ImportPreview? = null,
    val loadingCar: Boolean = false,
    val importing: Boolean = false,
    val saving: Boolean = false,
    val loadError: String? = null,
    val linkError: String? = null,
    val importError: String? = null,
    val saveError: String? = null,
    val savedId: String? = null,
)

@HiltViewModel
class CarFormViewModel @Inject constructor(
    private val vehicles: VehicleFormUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CarFormUiState())
    internal val state = mutableState.asStateFlow()
    val isDemo: Boolean get() = vehicles.isDemo
    private var initialImportRequested = false
    private var previewVersion = 0

    fun load(carId: String) {
        val current = state.value
        if (current.loadingCar || current.original?.id == carId) return
        mutableState.update { it.copy(loadingCar = true, loadError = null) }
        viewModelScope.launch {
            try {
                val car = vehicles.getCar(carId)
                mutableState.update { it.copy(original = car, loadingCar = false) }
            } catch (cancelled: CancellationException) {
                mutableState.update { it.copy(loadingCar = false) }
                throw cancelled
            } catch (error: Exception) {
                val message = when ((error as? RepositoryException)?.code) {
                    "not_found" -> "Автомобиль недоступен. Проверьте выборку."
                    else -> "Не удалось загрузить автомобиль."
                }
                mutableState.update { it.copy(loadError = message, loadingCar = false) }
            }
        }
    }

    internal fun prepareEdit(car: Car) {
        if (!state.value.saving) mutableState.value = CarFormUiState(original = car)
    }

    internal fun finishEdit() {
        if (!state.value.saving) mutableState.value = CarFormUiState()
    }

    fun clearLinkError() {
        mutableState.update { it.copy(linkError = null, importError = null) }
    }

    fun clearSaveError() {
        mutableState.update { it.copy(saveError = null) }
    }

    internal fun requestInitialImport(url: String) {
        if (initialImportRequested) return
        initialImportRequested = true
        importListing(url)
    }

    fun importListing(rawUrl: String) {
        val current = state.value
        if (current.importing || current.saving || current.savedId != null) return
        val url = rawUrl.trim()
        val validation = listingLinkError(url)
        if (validation != null) {
            mutableState.update { it.copy(linkError = validation, importError = null) }
            return
        }
        mutableState.update { it.copy(importing = true, linkError = null, importError = null, saveError = null) }
        viewModelScope.launch {
            try {
                val listing = vehicles.importListing(url)
                previewVersion++
                mutableState.update {
                    it.copy(preview = ImportPreview(url, listing, previewVersion), importing = false)
                }
            } catch (cancelled: CancellationException) {
                mutableState.update { it.copy(importing = false) }
                throw cancelled
            } catch (error: Exception) {
                val message = when ((error as? RepositoryException)?.code) {
                    "invalid_url" -> "Укажите ссылку на объявление Дрома."
                    "source_forbidden" -> "У источника нет доступных данных для импорта. Попробуйте ручной ввод."
                    "listing_unavailable" -> "Объявление недоступно. Проверьте ссылку или добавьте автомобиль вручную."
                    "parse_error" -> "Не удалось прочитать данные объявления. Их можно ввести вручную."
                    "source_unavailable" -> "Источник временно недоступен. Повторите попытку или введите данные вручную."
                    else -> "Не удалось получить данные. Повторите попытку или добавьте автомобиль вручную."
                }
                mutableState.update { it.copy(importError = message, importing = false) }
            }
        }
    }

    fun save(input: CarInput) {
        val current = state.value
        if (current.loadingCar || current.saving || current.savedId != null) return
        mutableState.update { it.copy(saving = true, saveError = null) }
        viewModelScope.launch {
            try {
                val saved = if (current.original == null) {
                    vehicles.createCar(input)
                } else {
                    vehicles.updateCar(current.original, input)
                }
                mutableState.update { it.copy(savedId = saved.id, saving = false) }
            } catch (cancelled: CancellationException) {
                mutableState.update { it.copy(saving = false) }
                throw cancelled
            } catch (error: Exception) {
                val message = when ((error as? RepositoryException)?.code) {
                    "not_found" -> "Автомобиль недоступен. Проверьте выборку."
                    "validation_error" -> "Проверьте заполненные поля. Введённые данные остались в форме."
                    else -> "Введённые данные остались в форме. Повторите попытку."
                }
                mutableState.update { it.copy(saveError = message, saving = false) }
            }
        }
    }

    fun consumeSaved(id: String) {
        mutableState.update { if (it.savedId == id) it.copy(savedId = null) else it }
    }
}

internal fun listingLinkError(rawUrl: String): String? {
    val url = rawUrl.trim()
    if (url.isEmpty()) return "Введите ссылку на объявление."
    val uri = runCatching { URI(url) }.getOrNull()?.takeIf(URI::isAbsolute)
        ?: return "Укажите полный адрес."
    val host = uri.host?.lowercase(Locale.ROOT)
    val scheme = uri.scheme.lowercase(Locale.ROOT)
    return if (
        (scheme == "https" || scheme == "http") &&
        host != null &&
        (host == "drom.ru" || host.endsWith(".drom.ru")) &&
        uri.userInfo == null
    ) null else "Укажите ссылку на объявление Дрома."
}

internal fun isDromListingLink(value: String): Boolean = listingLinkError(value) == null
