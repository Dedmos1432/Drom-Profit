package ru.dedmos.dromprofit.domain.usecases.selection

import ru.dedmos.dromprofit.domain.Car
import ru.dedmos.dromprofit.domain.Summary
import ru.dedmos.dromprofit.domain.repository.CarRepository

class LoadSelectionUseCase(private val repository: CarRepository) {
    val isDemo: Boolean get() = repository.isDemo
    val requiresNewSession get() = repository.requiresNewSession

    suspend operator fun invoke(): List<Car> = repository.getCars()

    suspend fun summary(carId: String): Summary = repository.getSummary(carId)

    suspend fun loadDemoCars(): List<Car> = repository.loadDemoCars()
}
