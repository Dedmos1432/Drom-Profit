package ru.dedmos.dromprofit.domain.usecases.session

import ru.dedmos.dromprofit.domain.repository.CarRepository

class RestartSessionUseCase(private val repository: CarRepository) {
    suspend operator fun invoke() = repository.restartSession()
}
