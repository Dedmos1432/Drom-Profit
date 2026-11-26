package ru.dedmos.dromprofit.di

import android.content.Context
import ru.dedmos.dromprofit.BuildConfig
import ru.dedmos.dromprofit.data.DemoCarRepository
import ru.dedmos.dromprofit.data.DemoScenario
import ru.dedmos.dromprofit.data.local.CarDao
import ru.dedmos.dromprofit.domain.repository.CarRepository

internal fun createVariantRepository(context: Context, cars: CarDao): CarRepository =
    if (BuildConfig.DROM_PROFIT_API_BASE_URL.isBlank()) {
        val scenario = if (BuildConfig.DROM_PROFIT_DEMO_EMPTY) {
            DemoScenario.AlternateEmpty
        } else {
            DemoScenario.entries.first { it.key == BuildConfig.DROM_PROFIT_DEMO_SCENARIO }
        }
        DemoCarRepository(context, cars, scenario)
    } else {
        createNetworkRepository(context, cars)
    }
