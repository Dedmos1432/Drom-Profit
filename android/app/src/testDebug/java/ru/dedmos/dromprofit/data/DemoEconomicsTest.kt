package ru.dedmos.dromprofit.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class DemoEconomicsTest {
    @Test
    fun matchesAllSharedCalculationVectors() {
        val fixturePath = requireNotNull(System.getProperty("dromProfitCalculationVectors"))
        val vectors = File(fixturePath).bufferedReader(Charsets.UTF_8).use { JsonParser.parseReader(it).asJsonArray }
        for (row in vectors) {
            val vector = row.asJsonObject
            val name = vector.requiredString("name")
            val cars = vector.objects("cars").map { it.withRequiredCarFields().readCar() }
            val calculated = demoCompare(cars)
            val expected = vector.getAsJsonObject("expected")
            val codes = expected.getAsJsonArray("warning_codes").map { it.asString }.toSet()
            expected.add("warnings", JsonArray())
            val contract = expected.readComparison()
            assertEquals(name, contract.items, calculated.items)
            assertEquals(name, contract.provisional, calculated.provisional)
            assertEquals(name, codes, calculated.warnings.map { it.code }.toSet())
        }
    }

    // The shared vectors include minimal cars with optional metadata intentionally omitted.
    private fun JsonObject.withRequiredCarFields() = deepCopy().apply {
        if (!has("source")) addProperty("source", "demo")
        if (!has("brand")) addProperty("brand", "Demo")
        if (!has("model")) addProperty("model", "Car")
        if (!has("notes")) add("notes", JsonArray())
        for (name in listOf("expenses", "benefits")) {
            objects(name).forEachIndexed { index, item ->
                if (!item.has("id")) item.addProperty("id", index.toString())
                if (!item.has("title")) item.addProperty("title", name)
            }
        }
    }
}
