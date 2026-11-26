package ru.dedmos.dromprofit.data

import com.google.gson.JsonArray
import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import ru.dedmos.dromprofit.domain.Benefit
import ru.dedmos.dromprofit.domain.Expense
import ru.dedmos.dromprofit.domain.ExpenseInput
import ru.dedmos.dromprofit.domain.Note
import ru.dedmos.dromprofit.domain.NoteKind
import ru.dedmos.dromprofit.domain.ExpenseKind
import ru.dedmos.dromprofit.domain.usecases.selection.LoadSelectionUseCase
import ru.dedmos.dromprofit.domain.repository.RepositoryException
import ru.dedmos.dromprofit.domain.usecases.vehicleform.VehicleFormUseCase
import ru.dedmos.dromprofit.domain.toInput
import ru.dedmos.dromprofit.domain.toListingData

class NetworkCarRepositoryTest {
    @Test
    fun importPreviewKeepsSelectionEmptyUntilConfirmed() = runTest {
        val token = MemoryTextFile(null)
        val fixtureUrl = "https://auto.drom.ru/demo/1.html"
        val photos = listOf("a.jpg", "b.jpg", "c.jpg").map { "/demo/vehicles/" + it }
        val gallery = JsonArray().apply { photos.forEach { add(it) } }
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
                             "listing_url":"$fixtureUrl","price":1200000,"image_url":"${photos.first()}","image_urls":$gallery}
                        """.trimIndent()
                    }
                    "POST" to "/api/cars" -> {
                        val input = JsonParser.parseString(body).asJsonObject
                        assertEquals("demo-001", input.get("listing_id").asString)
                        assertEquals(fixtureUrl, input.get("listing_url").asString)
                        assertEquals(1_210_000L, input.get("price").asLong)
                        assertEquals(gallery, input.getAsJsonArray("image_urls"))
                        assertEquals(photos.first(), input.get("image_url").asString)
                        val result = JsonParser.parseString(carJson).asJsonObject.apply {
                            addProperty("price", 1_210_000L)
                            addProperty("image_url", photos.first())
                        }
                        savedCarJson = result.toString()
                        201 to result.toString()
                    }
                    "GET" to "/api/cars" -> 200 to "[${savedCarJson.orEmpty()}]"
                    "GET" to "/api/cars/car-1" -> 200 to JsonParser.parseString(savedCarJson).asJsonObject.apply {
                        add("image_urls", gallery)
                    }.toString()
                    else -> error("Unexpected $method $path")
                }
            }
        }
        val form = VehicleFormUseCase(repository)
        val selection = LoadSelectionUseCase(repository)
        val preview = form.importListing(fixtureUrl)
        assertEquals("test-session", token.value)
        assertEquals(photos, preview.imageUrls)
        assertTrue(selection().isEmpty())

        val car = form.createCar(preview.toCarInput().copy(price = 1_210_000L))
        assertEquals(1_210_000L, car.price)
        assertEquals(car, selection().single())
        assertEquals(photos, repository.getCar(car.id).imageUrls)
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
                val patch = JsonParser.parseString(body).asJsonObject
                assertEquals(setOf("amount"), patch.keySet())
                assertTrue(patch.get("amount").isJsonNull)
                200 to """{"id":"expense-1","title":"ТО","kind":"maintenance","amount":null,"comment":"updated elsewhere"}"""
            } else {
                assertEquals("DELETE", method)
                204 to ""
            }
        }
        val original = Expense("expense-1", "ТО", ExpenseKind.MAINTENANCE, 0)
        val expense = repository.saveExpense("car-1", original.toInput().copy(amount = null), original)
        assertNull(expense.amount)
        assertEquals("updated elsewhere", expense.comment)
        repository.deleteExpense("car-1", expense.id)
    }

    @Test
    fun onlyEditedFieldsAreSentAndUnchangedRecordsSkipNetwork() = runTest {
        val benefit = Benefit("b1", "Комплект", null, "old-key", "old comment")
        val note = Note("n1", NoteKind.INSPECTION, "Сохранить текст")
        val expense = Expense("e1", "Ремонт", ExpenseKind.REPAIR, 0, "factor", "original")
        var requests = 0
        val repository = repository(MemoryTextFile("token")) { method, path, _, body ->
            requests++
            assertEquals("PATCH", method)
            val patch = JsonParser.parseString(body).asJsonObject
            when (path) {
                "/api/cars/car-1/benefits/b1" -> {
                    assertEquals(setOf("amount", "factor_key", "comment"), patch.keySet())
                    assertEquals("0", patch.get("amount").toString())
                    assertTrue(patch.get("factor_key").isJsonNull)
                    assertTrue(patch.get("comment").isJsonNull)
                    200 to """{"id":"b1","title":"updated elsewhere","amount":0,"factor_key":null,"comment":null}"""
                }
                "/api/cars/car-1/expenses/e1" -> {
                    assertEquals(setOf("title", "kind", "factor_key", "comment"), patch.keySet())
                    assertEquals("maintenance", patch.get("kind").asString)
                    assertEquals("new", patch.get("factor_key").asString)
                    assertEquals("new comment", patch.get("comment").asString)
                    200 to """{"id":"e1","title":"ТО","kind":"maintenance","amount":50,"factor_key":"new","comment":"new comment"}"""
                }
                "/api/cars/car-1/notes/n1" -> {
                    assertEquals(setOf("kind"), patch.keySet())
                    200 to """{"id":"n1","kind":"seller","text":"updated elsewhere"}"""
                }
                else -> error(path)
            }
        }
        assertEquals(benefit, repository.saveBenefit("car-1", benefit.toInput(), benefit))
        assertEquals(expense, repository.saveExpense("car-1", expense.toInput(), expense))
        assertEquals(note, repository.saveNote("car-1", note.toInput(), note))
        assertEquals(0, requests)
        val benefitResult = repository.saveBenefit("car-1", benefit.toInput().copy(amount = 0, factorKey = null, comment = null), benefit)
        assertEquals("updated elsewhere", benefitResult.title)
        assertEquals(0L, benefitResult.amount)
        val expenseResult = repository.saveExpense("car-1", expense.toInput().copy(
            title = "ТО", kind = ExpenseKind.MAINTENANCE, factorKey = "new", comment = "new comment"
        ), expense)
        assertEquals(50L, expenseResult.amount)
        assertEquals("updated elsewhere", repository.saveNote("car-1", note.toInput().copy(kind = NoteKind.SELLER), note).text)
        assertEquals(3, requests)
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
        assertTrue(repository.requiresNewSession.value)
        assertEquals("old", token.value)
        assertEquals(0, issued)
        repository.restartSession()
        assertFalse(repository.requiresNewSession.value)
        assertEquals("new", token.value)
        assertEquals(1, issued)
        assertTrue(repository.getCars().isEmpty())
    }

    @Test
    fun serverComparisonPreservesContractMoneyLimits() = runTest {
        val repository = repository(MemoryTextFile("token")) { method, path, _, body ->
            assertEquals("POST", method)
            assertEquals("/api/comparison", path)
            assertEquals("""{"car_ids":["car-1","car-2"]}""", body)
            200 to """
                {
                  "items": [{
                    "car_id": "car-1",
                    "listing_price": 1000000000000,
                    "total_expenses": 1000000000000,
                    "total_benefits": 0,
                    "acquisition_cost": 2000000000000,
                    "adjusted_cost": 2000000000000,
                    "assessment_status": "complete",
                    "unknown_expenses": 0,
                    "unknown_benefits": 0,
                    "difference_from_min": 2999999999999
                  }],
                  "warnings": [],
                  "provisional": false
                }
            """.trimIndent()
        }
        val comparison = repository.compare(listOf("car-1", "car-2"))
        assertEquals(2_000_000_000_000L, comparison.items.single().summary.adjustedCost)
        assertEquals(2_999_999_999_999L, comparison.items.single().differenceFromMin)
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
