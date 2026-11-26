package ru.dedmos.dromprofit.data

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.HttpException
import retrofit2.Response
import ru.dedmos.dromprofit.data.local.NetworkCarMirror
import ru.dedmos.dromprofit.domain.Benefit
import ru.dedmos.dromprofit.domain.BenefitInput
import ru.dedmos.dromprofit.domain.Car
import ru.dedmos.dromprofit.domain.CarInput
import ru.dedmos.dromprofit.domain.repository.CarRepository
import ru.dedmos.dromprofit.domain.Comparison
import ru.dedmos.dromprofit.domain.Expense
import ru.dedmos.dromprofit.domain.ExpenseInput
import ru.dedmos.dromprofit.domain.ListingData
import ru.dedmos.dromprofit.domain.Note
import ru.dedmos.dromprofit.domain.NoteInput
import ru.dedmos.dromprofit.domain.repository.RepositoryException
import ru.dedmos.dromprofit.domain.Summary
import ru.dedmos.dromprofit.domain.toInput
import java.io.IOException

internal class NetworkCarRepository(
    private val api: ProfitApi?,
    private val tokenFile: TextFile?,
    private val mirror: NetworkCarMirror? = null
) : CarRepository {
    override val isDemo = false
    private val sessionMutex = Mutex()
    private val mirrorMutex = Mutex()
    private val sessionRejected = MutableStateFlow(false)
    override val requiresNewSession = sessionRejected.asStateFlow()

    override suspend fun restartSession() {
        mirrorMutex.withLock {
            apiCall { service ->
                sessionMutex.withLock {
                    val oldToken = storedToken()
                    val newToken = createAndStoreToken(service)
                    if (oldToken != null && oldToken != newToken) mirror?.clear("Bearer " + oldToken)
                    sessionRejected.value = false
                }
            }
        }
    }

    override suspend fun getCars(): List<Car> = authorizedWithMirror { service, token ->
        val cars = service.getCars(token).readJson().asJsonArray.map { it.asJsonObject.readCar() }
        mirror?.replace(token, cars) ?: cars
    }

    override suspend fun getCar(carId: String): Car = authorizedWithMirror { service, token ->
        val car = service.getCar(token, carId).readObject().readCar()
        mirror?.save(token, car) ?: car
    }

    override suspend fun importListing(url: String): ListingData = authorized { service, token ->
        val body = JsonObject().apply { addProperty("url", url) }
        service.importListing(token, body.asBody()).readObject().readListing()
    }

    override suspend fun createCar(input: CarInput): Car = authorizedWithMirror { service, token ->
        val car = service.createCar(token, input.toJson().asBody()).readObject().readCar()
        mirror?.save(token, car, hasGallery = false) ?: car
    }

    override suspend fun updateCar(original: Car, input: CarInput): Car {
        val changes = input.changesFrom(original)
        return patchCar(original.id, changes)
    }

    override suspend fun updateReviewed(
        carId: String,
        expensesReviewed: Boolean?,
        benefitsReviewed: Boolean?
    ): Car = patchCar(
        carId,
        JsonObject().apply {
            expensesReviewed?.let { addProperty("expenses_reviewed", it) }
            benefitsReviewed?.let { addProperty("benefits_reviewed", it) }
        }
    )

    private suspend fun patchCar(carId: String, body: JsonObject): Car =
        authorizedWithMirror { service, token ->
            val response = if (body.size() == 0) {
                service.getCar(token, carId)
            } else {
                service.updateCar(token, carId, body.asBody())
            }
            val car = response.readObject().readCar()
            mirror?.save(token, car, hasGallery = body.size() == 0) ?: car
        }

    override suspend fun deleteCar(carId: String) {
        mutateRecord(carId) { service, token -> service.deleteCar(token, carId).checkDeleted() }
    }

    override suspend fun saveExpense(carId: String, input: ExpenseInput, original: Expense?): Expense {
        val body = input.toJson()
        if (original != null && body.keepChangesFrom(original.toInput().toJson()).size() == 0) {
            return original
        }
        return mutateRecord(carId) { service, token ->
            val response = if (original == null) {
                service.createExpense(token, carId, body.asBody())
            } else {
                service.updateExpense(token, carId, original.id, body.asBody())
            }
            response.readObject().readExpense()
        }
    }

    override suspend fun deleteExpense(carId: String, expenseId: String) =
        mutateRecord(carId) { service, token -> service.deleteExpense(token, carId, expenseId).checkDeleted() }

    override suspend fun saveBenefit(carId: String, input: BenefitInput, original: Benefit?): Benefit {
        val body = input.toJson()
        if (original != null && body.keepChangesFrom(original.toInput().toJson()).size() == 0) {
            return original
        }
        return mutateRecord(carId) { service, token ->
            val response = if (original == null) {
                service.createBenefit(token, carId, body.asBody())
            } else {
                service.updateBenefit(token, carId, original.id, body.asBody())
            }
            response.readObject().readBenefit()
        }
    }

    override suspend fun deleteBenefit(carId: String, benefitId: String) =
        mutateRecord(carId) { service, token -> service.deleteBenefit(token, carId, benefitId).checkDeleted() }

    override suspend fun saveNote(carId: String, input: NoteInput, original: Note?): Note {
        val body = input.toJson()
        if (original != null && body.keepChangesFrom(original.toInput().toJson()).size() == 0) {
            return original
        }
        return mutateRecord(carId) { service, token ->
            val response = if (original == null) {
                service.createNote(token, carId, body.asBody())
            } else {
                service.updateNote(token, carId, original.id, body.asBody())
            }
            response.readObject().readNote()
        }
    }

    override suspend fun deleteNote(carId: String, noteId: String) =
        mutateRecord(carId) { service, token -> service.deleteNote(token, carId, noteId).checkDeleted() }

    override suspend fun getSummary(carId: String): Summary = authorized { service, token ->
        service.getSummary(token, carId).readObject().readSummary()
    }

    override suspend fun compare(carIds: List<String>): Comparison = authorized { service, token ->
        val body = JsonObject().apply {
            add("car_ids", JsonArray().apply { carIds.forEach { add(it) } })
        }
        service.compare(token, body.asBody()).readObject().readComparison()
    }

    private suspend fun <T> mutateRecord(
        carId: String,
        call: suspend (ProfitApi, String) -> T
    ): T = authorizedWithMirror { service, token ->
        val result = call(service, token)
        // Nested routes return a record rather than the updated car and its review flags.
        mirror?.invalidate(token, carId)
        result
    }

    private suspend fun <T> authorizedWithMirror(call: suspend (ProfitApi, String) -> T): T =
        mirrorMutex.withLock { authorized(call) }

    private suspend fun <T> authorized(call: suspend (ProfitApi, String) -> T): T =
        apiCall { service ->
            val token = sessionMutex.withLock { storedToken() ?: createAndStoreToken(service) }
            try {
                call(service, "Bearer " + token)
            } catch (error: HttpException) {
                if (error.code() == 401) sessionMutex.withLock {
                    if (storedToken() == token) sessionRejected.value = true
                }
                throw error
            }
        }

    private suspend fun <T> apiCall(call: suspend (ProfitApi) -> T): T =
        withContext(Dispatchers.IO) {
            val service = api ?: throw RepositoryException(
                "configuration",
                "Адрес сервера Drom Profit не настроен."
            )
            try {
                call(service)
            } catch (error: CancellationException) {
                throw error
            } catch (error: HttpException) {
                throw error.toRepositoryException()
            } catch (error: IOException) {
                throw RepositoryException("network_error", "Не удалось связаться с сервером.", error)
            } catch (error: JsonParseException) {
                throw invalidResponse(error)
            } catch (error: IllegalStateException) {
                throw invalidResponse(error)
            } catch (error: IllegalArgumentException) {
                throw invalidResponse(error)
            }
        }

    private fun storedToken() = try {
        checkNotNull(tokenFile).readOrNull()?.takeIf(String::isNotBlank)
    } catch (error: IOException) {
        throw sessionStorageError(error)
    }

    private suspend fun createAndStoreToken(service: ProfitApi): String {
        val response = service.createSession().readObject()
        val token = response.requiredString("token")
        require(response.requiredString("token_type") == "bearer")
        require(token.isNotBlank() && token.all { it in '!'..'~' })
        try {
            checkNotNull(tokenFile).write(token)
        } catch (error: IOException) {
            throw sessionStorageError(error)
        }
        return token
    }
}

private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

private fun JsonObject.asBody() = toString().toRequestBody(jsonMediaType)

private fun ResponseBody.readJson(): JsonElement = use {
    JsonParser.parseReader(it.charStream())
}

private fun ResponseBody.readObject(): JsonObject = readJson().asJsonObject

private fun Response<Unit>.checkDeleted() {
    if (!isSuccessful) throw HttpException(this)
}

private fun HttpException.toRepositoryException(): RepositoryException {
    val serverError = runCatching { response()?.errorBody()?.readObject() }.getOrNull()
    val status = code()
    if (status == 401) return RepositoryException(
        "unauthorized",
        "Сервер не принял сохранённую сессию.",
        this
    )
    val code = runCatching { serverError?.stringOrNull("code") }.getOrNull()
    val message = runCatching { serverError?.stringOrNull("message") }.getOrNull()
    return RepositoryException(
        code ?: "http_" + status,
        message?.takeIf(String::isNotBlank) ?: "Сервер вернул ошибку (" + status + ").",
        this
    )
}

private fun invalidResponse(cause: Throwable) =
    RepositoryException("invalid_response", "Сервер вернул данные в неожиданном формате.", cause)

private fun sessionStorageError(cause: Throwable) =
    RepositoryException("session_storage", "Не удалось прочитать или сохранить сессию.", cause)
