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

    fun load(carId: String) {
        val current = state.value
        if (current.loadingCar || current.original?.id == carId) return
        mutableState.update { it.copy(loadingCar = true, loadError = null) }
        viewModelScope.launch {
            try {
                val car = vehicles.getCar(carId)
                mutableState.update { it.copy(original = car) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val message = when ((error as? RepositoryException)?.code) {
                    "not_found" -> "Автомобиль недоступен. Проверьте выборку."
                    else -> "Не удалось загрузить автомобиль."
                }
                mutableState.update { it.copy(loadError = message) }
            } finally {
                mutableState.update { it.copy(loadingCar = false) }
            }
        }
    }

    fun clearLinkError() {
        mutableState.update { it.copy(linkError = null, importError = null) }
    }

    fun clearSaveError() {
        mutableState.update { it.copy(saveError = null) }
    }

    fun importListing(rawUrl: String) {
        val current = state.value
        if (current.importing || current.saving || current.savedId != null) return
        val url = rawUrl.trim()
        val linkError = when {
            url.isEmpty() -> "Введите ссылку на объявление."
            !isDromListingLink(url) -> "Укажите полную ссылку на объявление Дрома."
            else -> null
        }
        if (linkError != null) {
            mutableState.update { it.copy(linkError = linkError, importError = null) }
            return
        }
        mutableState.update {
            it.copy(importing = true, linkError = null, importError = null, saveError = null)
        }
        viewModelScope.launch {
            try {
                val listing = vehicles.importListing(url)
                mutableState.update {
                    it.copy(preview = ImportPreview(url, listing, (it.preview?.version ?: 0) + 1))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val message = when ((error as? RepositoryException)?.code) {
                    "invalid_url" -> "Ссылка на объявление не поддерживается."
                    "source_forbidden" -> "Источник ограничил доступ к объявлению."
                    "listing_unavailable" -> "Объявление недоступно. Проверьте ссылку."
                    "parse_error" -> "Не удалось прочитать данные объявления."
                    "source_unavailable" -> "Источник объявлений временно недоступен."
                    else -> "Не удалось получить данные объявления."
                }
                mutableState.update { it.copy(importError = "$message Можно ввести данные вручную.") }
            } finally {
                mutableState.update { it.copy(importing = false) }
            }
        }
    }

    fun save(input: CarInput) {
        val current = state.value
        if (current.loadingCar || current.importing || current.saving || current.savedId != null) return
        mutableState.update { it.copy(saving = true, saveError = null) }
        viewModelScope.launch {
            try {
                val saved = if (current.original == null) {
                    vehicles.createCar(input)
                } else {
                    vehicles.updateCar(current.original, input)
                }
                mutableState.update { it.copy(savedId = saved.id) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val message = when ((error as? RepositoryException)?.code) {
                    "not_found" -> "Автомобиль недоступен. Проверьте выборку."
                    "validation_error" -> "Сервер отклонил данные. Проверьте заполненные поля."
                    else -> "Не удалось сохранить данные. Повторите попытку."
                }
                mutableState.update { it.copy(saveError = message) }
            } finally {
                mutableState.update { it.copy(saving = false) }
            }
        }
    }

    fun consumeSaved(id: String) {
        mutableState.update { if (it.savedId == id) it.copy(savedId = null) else it }
    }
}

internal fun isDromListingLink(value: String): Boolean {
    val uri = runCatching { URI(value) }.getOrNull() ?: return false
    val host = uri.host?.lowercase(Locale.ROOT) ?: return false
    val scheme = uri.scheme?.lowercase(Locale.ROOT)
    return (scheme == "https" || scheme == "http") &&
        (host == "drom.ru" || host.endsWith(".drom.ru")) && uri.userInfo == null
}
