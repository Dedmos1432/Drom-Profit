package ru.dedmos.dromprofit.presentation.viewmodels

import ru.dedmos.dromprofit.domain.repository.RepositoryException

internal sealed interface ScreenState<out T> {
    data object Loading : ScreenState<Nothing>
    data class Content<T>(val data: T) : ScreenState<T>
    data class Error(
        val message: String,
        val sessionExpired: Boolean = false,
        val restartError: String? = null
    ) : ScreenState<Nothing>
}

internal fun Exception.screenError(fallback: String): ScreenState.Error =
    if (this is RepositoryException) {
        ScreenState.Error(message ?: fallback, sessionExpired = code == "unauthorized")
    } else {
        ScreenState.Error(fallback)
    }
