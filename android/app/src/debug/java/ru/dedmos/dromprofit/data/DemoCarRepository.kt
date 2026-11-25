package ru.dedmos.dromprofit.data

import android.content.Context
import android.database.sqlite.SQLiteException
import android.util.AtomicFile
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.dedmos.dromprofit.data.local.CarDao
import ru.dedmos.dromprofit.data.local.CarEntity
import ru.dedmos.dromprofit.data.local.toDomain
import ru.dedmos.dromprofit.data.local.toEntity
import ru.dedmos.dromprofit.domain.Benefit
import ru.dedmos.dromprofit.domain.BenefitInput
import ru.dedmos.dromprofit.domain.Car
import ru.dedmos.dromprofit.domain.CarInput
import ru.dedmos.dromprofit.domain.repository.CarRepository
import ru.dedmos.dromprofit.domain.Comparison
import ru.dedmos.dromprofit.domain.Expense
import ru.dedmos.dromprofit.domain.ExpenseInput
import ru.dedmos.dromprofit.domain.ExpenseKind
import ru.dedmos.dromprofit.domain.ListingData
import ru.dedmos.dromprofit.domain.Note
import ru.dedmos.dromprofit.domain.NoteInput
import ru.dedmos.dromprofit.domain.repository.RepositoryException
import ru.dedmos.dromprofit.domain.Summary
import ru.dedmos.dromprofit.domain.toListingData
import java.io.File
import java.io.IOException
import java.net.URI
import java.util.UUID

internal class DemoCarRepository(
    context: Context,
    private val cars: CarDao,
    private val startEmpty: Boolean
) : CarRepository {
    override val isDemo = true
    private val assets = context.assets
    private val scope = if (startEmpty) "demo/empty" else "demo/seeded"
    private val legacyFile = if (startEmpty) "demo-cars-empty.json" else "demo-cars.json"
    private val legacyStorage = PrivateTextFile(AtomicFile(File(context.noBackupFilesDir, legacyFile)))
    private val mutex = Mutex()

    private val listings by lazy {
        assetObjects("import-listings.json").map(JsonObject::readListing).associateBy { it.sourceUrl }
    }

    override suspend fun restartSession() = withStore { cars.clearSelection(scope) }

    override suspend fun getCars(): List<Car> = withStore { cars.selection(scope).map(CarEntity::toDomain) }

    override suspend fun getCar(carId: String): Car = withStore { requireCar(carId) }

    override suspend fun importListing(url: String): ListingData = withStore {
        val uri = runCatching { URI(url) }.getOrNull()
        if (uri == null || uri.scheme != "https" || uri.host != "auto.drom.ru" || uri.rawAuthority != uri.host) {
            throw RepositoryException("invalid_url", "URL объявления не поддерживается.")
        }
        listings[url] ?: throw RepositoryException(
            "listing_unavailable",
            "Объявление недоступно в демонстрационном источнике."
        )
    }

    override suspend fun createCar(input: CarInput): Car = withStore {
        input.validate()
        cars.saveCar(input.toCar(newId()).toEntity(scope)).toDomain()
    }

    override suspend fun updateCar(original: Car, input: CarInput): Car = updateCar(original.id) { car ->
        input.validate()
        val changes = input.changesFrom(original)
        val json = car.toListingData().toCarInput().toJson()
        if (changes.entrySet().none { (key, value) -> json.get(key) != value }) {
            return@updateCar car
        }
        changes.entrySet().forEach { (key, value) -> json.add(key, value) }
        json.readListing().toCarInput().toCar(
            id = car.id,
            expenses = car.expenses,
            benefits = car.benefits,
            notes = car.notes
        )
    }

    override suspend fun updateReviewed(
        carId: String,
        expensesReviewed: Boolean?,
        benefitsReviewed: Boolean?
    ): Car = updateCar(carId) { car ->
        car.copy(
            expensesReviewed = expensesReviewed ?: car.expensesReviewed,
            benefitsReviewed = benefitsReviewed ?: car.benefitsReviewed
        )
    }

    override suspend fun deleteCar(carId: String) = withStore {
        if (cars.deleteCar(scope, carId) == 0) notFound()
    }

    override suspend fun saveExpense(carId: String, input: ExpenseInput, expenseId: String?): Expense {
        validateMoneyFactor(input.title, input.amount)
        val expense = Expense(expenseId ?: newId(), input.title, input.kind, input.amount, input.factorKey, input.comment)
        updateCar(carId) { car ->
            val updated = car.expenses.saveRecord(expense, expenseId == null, Expense::id)
            if (updated == car.expenses) car else car.copy(expenses = updated, expensesReviewed = false).validateFactors()
        }
        return expense
    }

    override suspend fun deleteExpense(carId: String, expenseId: String) {
        updateCar(carId) { car ->
            car.copy(expenses = car.expenses.removeRecord(expenseId, Expense::id), expensesReviewed = false)
        }
    }

    override suspend fun saveBenefit(carId: String, input: BenefitInput, benefitId: String?): Benefit {
        validateMoneyFactor(input.title, input.amount)
        val benefit = Benefit(benefitId ?: newId(), input.title, input.amount, input.factorKey, input.comment)
        updateCar(carId) { car ->
            val updated = car.benefits.saveRecord(benefit, benefitId == null, Benefit::id)
            if (updated == car.benefits) car else car.copy(benefits = updated, benefitsReviewed = false).validateFactors()
        }
        return benefit
    }

    override suspend fun deleteBenefit(carId: String, benefitId: String) {
        updateCar(carId) { car ->
            car.copy(benefits = car.benefits.removeRecord(benefitId, Benefit::id), benefitsReviewed = false)
        }
    }

    override suspend fun saveNote(carId: String, input: NoteInput, noteId: String?): Note {
        if (input.text.isBlank()) validationError()
        val note = Note(noteId ?: newId(), input.kind, input.text)
        updateCar(carId) { car -> car.copy(notes = car.notes.saveRecord(note, noteId == null, Note::id)) }
        return note
    }

    override suspend fun deleteNote(carId: String, noteId: String) {
        updateCar(carId) { car -> car.copy(notes = car.notes.removeRecord(noteId, Note::id)) }
    }

    override suspend fun getSummary(carId: String): Summary = withStore {
        demoSummary(requireCar(carId))
    }

    override suspend fun compare(carIds: List<String>): Comparison = withStore {
        if (carIds.size < 2 || carIds.toSet().size != carIds.size) validationError()
        val selection = cars.selection(scope).associateBy(CarEntity::id)
        demoCompare(carIds.map { selection[it]?.toDomain() ?: notFound() })
    }

    private suspend fun updateCar(carId: String, edit: (Car) -> Car): Car = withStore {
        val original = requireCar(carId)
        val saved = edit(original)
        if (saved == original) original else cars.saveCar(saved.toEntity(scope)).toDomain()
    }

    private suspend fun requireCar(carId: String): Car =
        cars.car(scope, carId)?.toDomain() ?: notFound()

    private suspend fun <T> withStore(action: suspend () -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                initializeSelection()
                action()
            } catch (error: CancellationException) {
                throw error
            } catch (error: SQLiteException) {
                throw demoStorageError(error)
            } catch (error: IOException) {
                throw demoStorageError(error)
            } catch (error: JsonParseException) {
                throw demoStorageError(error)
            } catch (error: IllegalStateException) {
                throw demoStorageError(error)
            } catch (error: IllegalArgumentException) {
                throw demoStorageError(error)
            }
        }
    }

    private suspend fun initializeSelection() {
        if (cars.isInitialized(scope)) return
        val legacy = legacyStorage.readOrNull()
        val objects = when {
            legacy != null -> parseObjects(legacy)
            startEmpty -> emptyList()
            else -> assetObjects("demo-cars.json")
        }
        val rows = objects.mapIndexed { index, json -> json.readCar().toEntity(scope, index.toLong()) }
        cars.initializeDemo(scope, rows)
    }

    private fun assetObjects(name: String): List<JsonObject> =
        assets.open(name).bufferedReader(Charsets.UTF_8).use { parseObjects(it.readText()) }

    private fun parseObjects(text: String): List<JsonObject> =
        JsonParser.parseString(text).asJsonArray.map { it.asJsonObject }
}

private fun newId() = UUID.randomUUID().toString()

private fun <T> List<T>.saveRecord(record: T, isNew: Boolean, id: (T) -> String): List<T> {
    if (isNew) return this + record
    val recordId = id(record)
    if (none { id(it) == recordId }) notFound()
    return map { if (id(it) == recordId) record else it }
}

private fun <T> List<T>.removeRecord(recordId: String, id: (T) -> String): List<T> =
    filterNot { id(it) == recordId }.also { if (it.size == size) notFound() }

private fun Car.validateFactors(): Car {
    val expenseKeys = expenses.mapNotNull { expense -> expense.factorKey?.let { expense.kind to it } }
    val benefitKeys = benefits.mapNotNull { it.factorKey }
    if (expenseKeys.size != expenseKeys.toSet().size || benefitKeys.size != benefitKeys.toSet().size) {
        throw RepositoryException("duplicate_factor", "Этот фактор уже добавлен в карточку.")
    }
    val missingKeys = expenseKeys.filter { it.first == ExpenseKind.MISSING_EQUIPMENT }.map { it.second }
    if (missingKeys.any { it in benefitKeys }) {
        throw RepositoryException("conflicting_equipment", "Учитывайте оснащение в карточке одним способом.")
    }
    return this
}

private fun CarInput.validate() {
    val validEngineVolume = engineVolume?.let { it.isFinite() && it >= 0 } ?: true
    val validUrls = listOfNotNull(sourceUrl, imageUrl).all { url ->
        runCatching { URI(url).isAbsolute }.getOrDefault(false)
    }
    if (brand.isBlank() || model.isBlank() || price <= 0 || (year != null && year < 1886) ||
        listOfNotNull(mileage, enginePower, marketPrice).any { it < 0 } || !validEngineVolume || !validUrls
    ) validationError()
}

private fun validateMoneyFactor(title: String, amount: Long?) {
    if (title.isBlank() || (amount != null && amount < 0)) validationError()
}

private fun notFound(): Nothing = throw RepositoryException("not_found", "Запись не найдена.")

private fun validationError(): Nothing = throw RepositoryException("validation_error", "Проверьте поля запроса.")

private fun demoStorageError(cause: Throwable) =
    RepositoryException("demo_storage", "Не удалось прочитать или сохранить демонстрационные данные.", cause)
