package ru.dedmos.dromprofit.di

import android.content.Context
import ru.dedmos.dromprofit.data.local.CarDao
import ru.dedmos.dromprofit.domain.repository.CarRepository

internal fun createVariantRepository(context: Context, cars: CarDao): CarRepository = createNetworkRepository(context, cars)
