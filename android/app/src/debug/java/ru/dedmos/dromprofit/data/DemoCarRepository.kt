package ru.dedmos.dromprofit.data

import android.content.Context
import android.database.sqlite.SQLiteException
import android.util.AtomicFile
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
import ru.dedmos.dromprofit.domain.MaxCarPhotos
import ru.dedmos.dromprofit.domain.MaxMoney
import ru.dedmos.dromprofit.domain.Note
import ru.dedmos.dromprofit.domain.NoteInput
import ru.dedmos.dromprofit.domain.repository.RepositoryException
import ru.dedmos.dromprofit.domain.Summary
import ru.dedmos.dromprofit.domain.photosWithCover
import ru.dedmos.dromprofit.domain.toInput
import ru.dedmos.dromprofit.domain.toListingData
import java.io.File
import java.io.IOException
import java.net.URI
import java.util.UUID

internal enum class DemoScenario(val key: String, val scope: String, val legacyFile: String? = null) {
    // Keep the storage scopes used by earlier debug builds.
    Empty("empty", "demo/seeded", "demo-cars.json"),
    AlternateEmpty("alternate-empty", "demo/empty", "demo-cars-empty.json"),
    Seeded("seeded", "demo/scenario/seeded"),
    Showcase("showcase", "demo/scenario/showcase"),
    ListError("list-error", "demo/scenario/list-error"),
}

internal class DemoCarRepository(
    context: Context,
    private val cars: CarDao,
    private val scenario: DemoScenario,
) : CarRepository {
    constructor(context: Context, cars: CarDao, startEmpty: Boolean) : this(
        context,
        cars,
        if (startEmpty) DemoScenario.AlternateEmpty else DemoScenario.Empty,
    )

    override val isDemo = true
    private val assets = context.assets
    private val scope = scenario.scope
    private val legacyStorage = scenario.legacyFile?.let { file ->
        PrivateTextFile(AtomicFile(File(context.noBackupFilesDir, file)))
    }
    private val mutex = Mutex()
    private var failNextList = scenario == DemoScenario.ListError

    private val showcaseListings by lazy { assetObjects("demo-showcase.json") }
    private val showcaseCars by lazy {
        val assessments = JsonParser.parseString(assetText("demo-showcase-assessments.json")).asJsonObject
        demoShowcaseCars(showcaseListings, assessments)
    }

    private val listings by lazy {
        (assetObjects("import-listings.json") + showcaseListings)
            .map(JsonObject::readListing).associateBy { it.sourceUrl }
    }

    override suspend fun restartSession() {
        withStore { cars.replaceSelection(scope, initialCars().toRows()) }
    }

    override suspend fun getCars(): List<Car> = withStore {
        if (failNextList) {
            failNextList = false
            throw RepositoryException("mock_list_error", "Не удалось загрузить выборку.")
        }
        cars.selection(scope).map(CarEntity::toDomain)
    }

    override suspend fun loadDemoCars(): List<Car> = withStore {
        val samples = seededCars().take(2).toRows()
        cars.fillEmptySelection(scope, samples).map(CarEntity::toDomain)
    }

    override suspend fun getCar(carId: String): Car = withStore { requireCar(carId) }

    override suspend fun importListing(url: String): ListingData = withStore {
        val uri = url.toHttpUrlOrNull()
        if (uri == null || (uri.host != "drom.ru" && !uri.host.endsWith(".drom.ru")) ||
            uri.username.isNotEmpty() || uri.password.isNotEmpty()
        ) {
            throw RepositoryException("invalid_url", "URL объявления не поддерживается.")
        }
        val canonicalUrl = uri.toString()
        when (canonicalUrl) {
            "https://auto.drom.ru/demo/forbidden.html" ->
                throw RepositoryException("source_forbidden", "Источник недоступен для импорта.")
            "https://auto.drom.ru/demo/parse-error.html" ->
                throw RepositoryException("parse_error", "Не удалось прочитать объявление.")
            "https://auto.drom.ru/demo/source-unavailable.html" ->
                throw RepositoryException("source_unavailable", "Источник временно недоступен.")
        }
        listings[canonicalUrl] ?: throw RepositoryException(
            "listing_unavailable",
            "Объявление недоступно в демонстрационном источнике."
        )
    }

    override suspend fun createCar(input: CarInput): Car = withStore {
        input.validate()
        cars.prependCar(input.toCar(newId()).toEntity(scope)).toDomain()
    }

    override suspend fun updateCar(original: Car, input: CarInput): Car = updateCar(original.id) { car ->
        input.validate()
        val changes = input.changesFrom(original)
        val json = car.toListingData().toCarInput().toJson()
        if (changes.entrySet().none { (key, value) -> json.get(key) != value }) return@updateCar car

        val listing = json.applyChanges(changes).apply {
            if (changes.has("image_url") || changes.has("image_urls")) {
                val photos = if (changes.has("image_urls")) {
                    changes.getAsJsonArray("image_urls").map { it.asString }.distinct()
                } else {
                    photosWithCover(car.imageUrls, changes.stringOrNull("image_url"))
                }
                if (photos.size > MaxCarPhotos) validationError()
                add("image_urls", JsonArray().apply { photos.forEach { add(it) } })
                addProperty("image_url", photos.firstOrNull())
            }
        }.readListing()
        val characteristicsChanged = changes.keySet().any { it != "image_url" && it != "image_urls" }
        listing.toCarInput().toCar(
            id = car.id,
            expensesReviewed = car.expensesReviewed && !characteristicsChanged,
            benefitsReviewed = car.benefitsReviewed && !characteristicsChanged,
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

    override suspend fun saveExpense(carId: String, input: ExpenseInput, original: Expense?): Expense {
        validateMoneyFactor(input.title, input.amount)
        val id = original?.id ?: newId()
        val changes = input.toJson()
        if (original != null && changes.keepChangesFrom(original.toInput().toJson()).size() == 0) {
            return original
        }
        val saved = updateCar(carId) { car ->
            val value = if (original == null) changes else {
                val current = car.expenses.firstOrNull { it.id == id } ?: notFound()
                current.toInput().toJson().applyChanges(changes)
            }
            val record = value.apply { addProperty("id", id) }.readExpense()
            val updated = car.expenses.saveRecord(record, original == null, Expense::id)
            if (updated == car.expenses) return@updateCar car
            demoGroupTotal(updated.map(Expense::amount))
            car.copy(expenses = updated, expensesReviewed = false).validateFactors()
        }
        return saved.expenses.first { it.id == id }
    }

    override suspend fun deleteExpense(carId: String, expenseId: String) {
        updateCar(carId) { car ->
            car.copy(expenses = car.expenses.removeRecord(expenseId, Expense::id), expensesReviewed = false)
        }
    }

    override suspend fun saveBenefit(carId: String, input: BenefitInput, original: Benefit?): Benefit {
        validateMoneyFactor(input.title, input.amount)
        val id = original?.id ?: newId()
        val changes = input.toJson()
        if (original != null && changes.keepChangesFrom(original.toInput().toJson()).size() == 0) {
            return original
        }
        val saved = updateCar(carId) { car ->
            val value = if (original == null) changes else {
                val current = car.benefits.firstOrNull { it.id == id } ?: notFound()
                current.toInput().toJson().applyChanges(changes)
            }
            val record = value.apply { addProperty("id", id) }.readBenefit()
            val updated = car.benefits.saveRecord(record, original == null, Benefit::id)
            if (updated == car.benefits) return@updateCar car
            demoGroupTotal(updated.map(Benefit::amount))
            car.copy(benefits = updated, benefitsReviewed = false).validateFactors()
        }
        return saved.benefits.first { it.id == id }
    }

    override suspend fun deleteBenefit(carId: String, benefitId: String) {
        updateCar(carId) { car ->
            car.copy(benefits = car.benefits.removeRecord(benefitId, Benefit::id), benefitsReviewed = false)
        }
    }

    override suspend fun saveNote(carId: String, input: NoteInput, original: Note?): Note {
        if (input.text.isBlank()) validationError()
        val id = original?.id ?: newId()
        val changes = input.toJson()
        if (original != null && changes.keepChangesFrom(original.toInput().toJson()).size() == 0) {
            return original
        }
        val saved = updateCar(carId) { car ->
            val value = if (original == null) changes else {
                val current = car.notes.firstOrNull { it.id == id } ?: notFound()
                current.toInput().toJson().applyChanges(changes)
            }
            val record = value.apply { addProperty("id", id) }.readNote()
            val updated = car.notes.saveRecord(record, original == null, Note::id)
            car.copy(notes = updated)
        }
        return saved.notes.first { it.id == id }
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
        val legacy = legacyStorage?.readOrNull()
        val initial = legacy?.let { parseObjects(it).map(JsonObject::readCar) } ?: initialCars()
        cars.initializeDemo(scope, initial.toRows())
    }

    private fun initialCars(): List<Car> = when (scenario) {
        DemoScenario.Seeded -> seededCars()
        DemoScenario.Showcase -> showcaseCars
        else -> emptyList()
    }

    private fun seededCars(): List<Car> = assetObjects("demo-cars.json").map { json ->
        val car = json.readCar()
        val modelPhoto = when (car.id) {
            "car-1", "car-3" -> "camry-xv50-silver.jpg"
            "car-2" -> "camry-xv50-white.jpg"
            else -> null
        }
        if (car.imageUrl == null && modelPhoto != null) {
            val path = "/$DemoPhotoDirectory/$modelPhoto"
            car.copy(imageUrl = path, imageUrls = listOf(path))
        } else car
    }

    private fun List<Car>.toRows(): List<CarEntity> = mapIndexed { index, car ->
        car.toEntity(scope, index.toLong())
    }

    private fun assetObjects(name: String): List<JsonObject> = parseObjects(assetText(name))

    private fun assetText(name: String): String =
        assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }

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
    val gallery = imageUrls ?: listOfNotNull(imageUrl)
    val validPhotos = gallery.size <= MaxCarPhotos && (imageUrl == null || gallery.firstOrNull() == imageUrl) &&
        gallery.all { parseDemoPhoto(it) != null || it.isAbsoluteUri() }
    if (brand.isBlank() || model.isBlank() || (year != null && year < 1886) ||
        listOfNotNull(mileage, enginePower).any { it < 0 } || !validEngineVolume ||
        !validPhotos || sourceUrl?.isAbsoluteUri() == false
    ) validationError()
    if (price !in 1L..MaxMoney || (marketPrice != null && marketPrice !in 1L..MaxMoney)) {
        demoAmountOutOfRange("Цена и указанная рыночная оценка должны быть от 1 до 1 000 000 000 000 ₽.")
    }
}

private fun String.isAbsoluteUri(): Boolean =
    runCatching { URI(this).isAbsolute }.getOrDefault(false)

private fun validateMoneyFactor(title: String, amount: Long?) {
    if (title.isBlank()) validationError()
    if (amount != null && amount !in 0L..MaxMoney) {
        demoAmountOutOfRange("Сумма должна быть от 0 до 1 000 000 000 000 ₽.")
    }
}

private fun notFound(): Nothing = throw RepositoryException("not_found", "Запись не найдена.")

private fun validationError(): Nothing = throw RepositoryException("validation_error", "Проверьте поля запроса.")

private fun demoStorageError(cause: Throwable) =
    RepositoryException("demo_storage", "Не удалось прочитать или сохранить демонстрационные данные.", cause)
