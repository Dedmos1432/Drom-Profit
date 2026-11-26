package ru.dedmos.dromprofit.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ru.dedmos.dromprofit.data.local.ProfitDatabase
import ru.dedmos.dromprofit.data.local.SelectionIdsStore
import ru.dedmos.dromprofit.domain.usecases.comparison.RememberComparisonUseCase
import ru.dedmos.dromprofit.domain.usecases.detail.CarDetailUseCases
import ru.dedmos.dromprofit.domain.repository.CarRepository
import ru.dedmos.dromprofit.domain.usecases.comparison.CompareCarsUseCase
import ru.dedmos.dromprofit.domain.usecases.selection.LoadSelectionUseCase
import ru.dedmos.dromprofit.domain.usecases.session.RestartSessionUseCase
import ru.dedmos.dromprofit.domain.usecases.vehicleform.VehicleFormUseCase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    internal fun provideProfitDatabase(@ApplicationContext context: Context): ProfitDatabase =
        Room.databaseBuilder(context, ProfitDatabase::class.java, "drom-profit.db").build()

    @Provides
    @Singleton
    internal fun provideCarRepository(
        @ApplicationContext context: Context,
        database: ProfitDatabase
    ): CarRepository = createVariantRepository(context, database.carDao())

    @Provides
    fun provideLoadSelection(repository: CarRepository) = LoadSelectionUseCase(repository)

    @Provides
    fun provideCompareCars(repository: CarRepository) = CompareCarsUseCase(repository)

    @Provides
    @Singleton
    internal fun provideRememberComparison(@ApplicationContext context: Context): RememberComparisonUseCase =
        RememberComparisonUseCase(SelectionIdsStore(context))

    @Provides
    fun provideRestartSession(repository: CarRepository) = RestartSessionUseCase(repository)

    @Provides
    fun provideVehicleForm(repository: CarRepository) = VehicleFormUseCase(repository)

    @Provides
    fun provideCarDetail(repository: CarRepository) = CarDetailUseCases(repository)
}
