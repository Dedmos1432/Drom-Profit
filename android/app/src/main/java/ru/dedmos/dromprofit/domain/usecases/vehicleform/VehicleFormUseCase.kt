package ru.dedmos.dromprofit.domain.usecases.vehicleform

import ru.dedmos.dromprofit.domain.Car
import ru.dedmos.dromprofit.domain.CarInput
import ru.dedmos.dromprofit.domain.ListingData
import ru.dedmos.dromprofit.domain.repository.CarRepository

class VehicleFormUseCase(private val repository: CarRepository) {
    val isDemo: Boolean get() = repository.isDemo

    suspend fun importListing(url: String): ListingData = repository.importListing(url)

    suspend fun createCar(input: CarInput): Car = repository.createCar(input)

    suspend fun updateCar(original: Car, input: CarInput): Car = repository.updateCar(original, input)

    suspend fun getCar(carId: String): Car = repository.getCar(carId)
}
