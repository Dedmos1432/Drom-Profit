package ru.dedmos.dromprofit.domain.usecases.comparison

internal interface ComparisonSelectionStore {
    fun read(): List<String>
    fun write(ids: List<String>)
}

internal class RememberComparisonUseCase(private val store: ComparisonSelectionStore) {
    fun read(): List<String> = store.read()
    fun write(ids: List<String>) = store.write(ids)
}
