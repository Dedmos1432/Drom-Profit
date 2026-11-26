package ru.dedmos.dromprofit.domain.usecases.comparison

import ru.dedmos.dromprofit.domain.Comparison
import ru.dedmos.dromprofit.domain.repository.CarRepository

class CompareCarsUseCase(private val repository: CarRepository) {
    suspend operator fun invoke(carIds: List<String>): Comparison = repository.compare(carIds)
}
