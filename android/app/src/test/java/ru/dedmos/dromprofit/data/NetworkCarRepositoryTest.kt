package ru.dedmos.dromprofit.data

import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import ru.dedmos.dromprofit.domain.ExpenseInput
import ru.dedmos.dromprofit.domain.ExpenseKind
import ru.dedmos.dromprofit.domain.usecases.selection.LoadSelectionUseCase
import ru.dedmos.dromprofit.domain.repository.RepositoryException
import ru.dedmos.dromprofit.domain.usecases.vehicleform.VehicleFormUseCase
import ru.dedmos.dromprofit.domain.toListingData

class NetworkCarRepositoryTest {
    @Test
    fun importPreviewKeepsSelectionEmptyUntilConfirmed() = runTest {
        val token = MemoryTextFile(null)
        val fixtureUrl = "https://auto.drom.ru/demo/1.html"
        var savedCarJson: String? = null
        val repository = repository(token) { method, path, authorization, body ->
            if (path == "/api/session") {
                assertEquals("POST", method)
                assertNull(authorization)
                201 to """{"token":"test-session","token_type":"bearer"}"""
            } else {
                assertEquals("Bearer test-session", authorization)
                when (method to path) {
                    "POST" to "/api/cars/import" -> {
                        assertEquals(fixtureUrl, JsonParser.parseString(body).asJsonObject.get("url").asString)
                        200 to """
                            {"source":"demo","listing_id":"demo-001","brand":"Toyota","model":"Camry",
                             "listing_url":"$fixtureUrl","price":1200000}
                        """.trimIndent()
                    }
                    "POST" to "/api/cars" -> {
                        val input = JsonParser.parseString(body).asJsonObject
                        assertEquals("demo-001", input.get("listing_id").asString)
                        assertEquals(fixtureUrl, input.get("listing_url").asString)
                        assertEquals(1_210_000L, input.get("price").asLong)
                        val result = carJson.replace("1200000", "1210000")
                        savedCarJson = result
                        201 to result
                    }
                    "GET" to "/api/cars" -> 200 to "[${savedCarJson.orEmpty()}]"
                    else -> error("Unexpected $method $path")
                }
            }
        }
        val form = VehicleFormUseCase(repository)
        val selection = LoadSelectionUseCase(repository)
        val preview = form.importListing(fixtureUrl)
        assertEquals("test-session", token.value)
        assertTrue(selection().isEmpty())

        val car = form.createCar(preview.toCarInput().copy(price = 1_210_000L))
        assertEquals(1_210_000L, car.price)
        assertEquals(car, selection().single())
    }

    @Test
    fun carPatchSendsExplicitNullOnlyForChangedEditableFields() = runTest {
        val original = JsonParser.parseString(carJson).asJsonObject.readCar()
        val input = original.toListingData().toCarInput().copy(
            mileage = null,
            listingId = "immutable",
            fetchedAt = "2026-01-01T00:00:00Z"
        )
        val repository = repository(MemoryTextFile("token")) { method, path, authorization, body ->
            assertEquals("PATCH", method)
            assertEquals("/api/cars/car-1", path)
            assertEquals("Bearer token", authorization)
            val patch = JsonParser.parseString(body).asJsonObject
            assertEquals(setOf("mileage_km"), patch.keySet())
            assertTrue(patch.get("mileage_km").isJsonNull)
            200 to carJson.replace("145000", "null")
        }
        assertNull(repository.updateCar(original, input).mileage)
    }

    @Test
    fun expensePatchTransmitsUnknownAmountAndDeleteAcceptsNoContent() = runTest {
        val repository = repository(MemoryTextFile("token")) { method, path, _, body ->
            assertEquals("/api/cars/car-1/expenses/expense-1", path)
            if (method == "PATCH") {
                assertTrue(JsonParser.parseString(body).asJsonObject.get("amount").isJsonNull)
                200 to """{"id":"expense-1","title":"ТО","kind":"maintenance","amount":null}"""
            } else {
                assertEquals("DELETE", method)
                204 to ""
            }
        }
        val expense = repository.saveExpense(
            "car-1", ExpenseInput("ТО", ExpenseKind.MAINTENANCE, amount = null), "expense-1"
        )
        assertNull(expense.amount)
        repository.deleteExpense("car-1", expense.id)
    }

    @Test
    fun unauthorizedSessionChangesOnlyAfterExplicitRestart() = runTest {
        val token = MemoryTextFile("old")
        var issued = 0
        val repository = repository(token) { method, path, authorization, _ ->
            if (method == "POST" && path == "/api/session") {
                assertNull(authorization)
                issued++
                201 to """{"token":"new","token_type":"bearer"}"""
            } else if (authorization == "Bearer old") {
                401 to """{"code":"unauthorized","message":"Требуется токен"}"""
            } else {
                assertEquals("Bearer new", authorization)
                200 to "[]"
            }
        }
        val error = runCatching { repository.getCars() }.exceptionOrNull() as RepositoryException
        assertEquals("unauthorized", error.code)
        assertEquals("old", token.value)
        assertEquals(0, issued)
        repository.restartSession()
        assertEquals("new", token.value)
        assertEquals(1, issued)
        assertTrue(repository.getCars().isEmpty())
    }

    @Test
    fun serverComparisonPreservesAmountsPastLongRange() = runTest {
        val repository = repository(MemoryTextFile("token")) { method, path, _, body ->
            assertEquals("POST", method)
            assertEquals("/api/comparison", path)
            assertEquals("""{"car_ids":["car-1","car-2"]}""", body)
            200 to """
                {
                  "items": [{
                    "car_id": "car-1",
                    "listing_price": 9223372036854775807,
                    "total_expenses": 9223372036854775807,
                    "total_benefits": 0,
                    "acquisition_cost": 18446744073709551614,
                    "adjusted_cost": 18446744073709551614,
                    "assessment_status": "complete",
                    "unknown_expenses": 0,
                    "unknown_benefits": 0,
                    "difference_from_min": 18446744073709551613
                  }],
                  "warnings": [],
                  "provisional": false
                }
            """.trimIndent()
        }
        val comparison = repository.compare(listOf("car-1", "car-2"))
        assertEquals("18446744073709551614".toBigInteger(), comparison.items.single().summary.adjustedCost)
        assertEquals("18446744073709551613".toBigInteger(), comparison.items.single().differenceFromMin)
    }

    private fun repository(
        token: TextFile,
        respond: (String, String, String?, String?) -> Pair<Int, String>
    ): NetworkCarRepository {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val body = request.body?.let { payload -> Buffer().apply { payload.writeTo(this) }.readUtf8() }
            val (status, json) = respond(request.method, request.url.encodedPath, request.header("Authorization"), body)
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(status)
                .message("Test response")
                .body(json.toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        val api = Retrofit.Builder()
            .baseUrl("https://test.invalid/")
            .client(client)
            .validateEagerly(true)
            .build()
            .create(ProfitApi::class.java)
        return NetworkCarRepository(api, token)
    }

    private class MemoryTextFile(var value: String?) : TextFile {
        override fun readOrNull() = value
        override fun write(value: String) {
            this.value = value
        }
    }

    private val carJson = """
        {
          "source": "demo",
          "listing_id": "demo-001",
          "brand": "Toyota",
          "model": "Camry",
          "price": 1200000,
          "mileage_km": 145000,
          "id": "car-1",
          "expenses_reviewed": true,
          "benefits_reviewed": true,
          "expenses": [],
          "benefits": [],
          "notes": []
        }
    """.trimIndent()
}
