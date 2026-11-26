package ru.dedmos.dromprofit.data.local

import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.dedmos.dromprofit.data.DemoCarRepository
import ru.dedmos.dromprofit.data.DemoScenario
import ru.dedmos.dromprofit.data.toCarInput
import ru.dedmos.dromprofit.domain.ExpenseInput
import ru.dedmos.dromprofit.domain.ExpenseKind
import ru.dedmos.dromprofit.domain.NoteInput
import ru.dedmos.dromprofit.domain.NoteKind
import ru.dedmos.dromprofit.domain.repository.RepositoryException
import java.io.File
import java.nio.file.Files

@RunWith(AndroidJUnit4::class)
class DemoCarRoomTest {
    @Test
    fun isolatesShowcaseSeededAndRetryScenarios() = runBlocking {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(targetContext, ProfitDatabase::class.java).build()
        try {
            val dao = database.carDao()
            val showcase = DemoCarRepository(targetContext, dao, DemoScenario.Showcase)
            val seeded = DemoCarRepository(targetContext, dao, DemoScenario.Seeded)
            val listError = DemoCarRepository(targetContext, dao, DemoScenario.ListError)

            val cars = showcase.getCars()
            assertEquals(listOf("101", "102", "103", "104", "105"), cars.map { it.listingId })
            assertTrue(cars.all { it.expenses.isNotEmpty() && it.imageUrl?.contains("?gallery=") == true })
            assertTrue(cars.first { it.listingId == "102" }.expenses.any { it.amount == null })
            assertEquals(listOf("car-1", "car-2", "car-3"), seeded.getCars().map { it.id })

            val listing = listError.importListing("https://auto.drom.ru/demo/101.html")
            val failure = runCatching { listError.getCars() }.exceptionOrNull() as? RepositoryException
            assertEquals("mock_list_error", failure?.code)
            assertTrue(listError.getCars().isEmpty())

            val imported = listError.createCar(listing.toCarInput())
            assertEquals("101", imported.listingId)
            assertTrue(imported.expenses.isEmpty())
            assertEquals(imported.id, listError.getCars().single().id)
            assertEquals(5, showcase.getCars().size)
        } finally {
            database.close()
        }
    }

    @Test
    fun migratesPrivateDemoSelectionAndPersistsEditsWithoutReseeding() = runBlocking {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = Files.createTempDirectory(targetContext.cacheDir.toPath(), "demo-db-test").toFile()
        val migrationContext = object : ContextWrapper(targetContext) {
            override fun getNoBackupFilesDir(): File = directory
        }
        val savedSelection = File(directory, "demo-cars.json")
        savedSelection.writeText(
            """
            [{
                "source": "manual", "id": "legacy-car", "brand": "Toyota", "model": "Camry",
                "price": 1200000, "expenses_reviewed": true, "benefits_reviewed": true,
                "expenses": [], "benefits": [], "notes": []
            }]
            """.trimIndent()
        )
        val database = Room.inMemoryDatabaseBuilder(targetContext, ProfitDatabase::class.java).build()
        try {
            val dao = database.carDao()
            val seeded = DemoCarRepository(migrationContext, dao, startEmpty = false)
            val empty = DemoCarRepository(migrationContext, dao, startEmpty = true)
            assertEquals(listOf("legacy-car"), seeded.getCars().map { it.id })
            assertTrue(empty.getCars().isEmpty())
            val expense = seeded.saveExpense(
                "legacy-car",
                ExpenseInput("Проверить кондиционер", ExpenseKind.REPAIR, amount = null)
            )
            seeded.saveNote("legacy-car", NoteInput(NoteKind.INSPECTION, "Не проверено"))

            val reopened = DemoCarRepository(migrationContext, dao, startEmpty = false)
            val car = reopened.getCar("legacy-car")
            assertEquals(expense.id, car.expenses.single().id)
            assertNull(car.expenses.single().amount)
            assertEquals("Не проверено", car.notes.single().text)
            assertFalse(car.expensesReviewed)
            assertTrue(car.benefitsReviewed)
            assertTrue(empty.getCars().isEmpty())

            reopened.deleteCar("legacy-car")
            val reopenedAfterDeletion = DemoCarRepository(migrationContext, dao, startEmpty = false)
            assertTrue(reopenedAfterDeletion.getCars().isEmpty())

            val samples = empty.loadDemoCars()
            assertEquals(2, samples.size)
            val sampleId = samples.first().id
            val added = empty.saveExpense(sampleId, ExpenseInput("Проверка", ExpenseKind.REPAIR, amount = 5_000))
            val samplesAfterRetry = empty.loadDemoCars()
            assertEquals(samples.map { it.id }, samplesAfterRetry.map { it.id })
            assertTrue(samplesAfterRetry.first().expenses.any { it.id == added.id })
            assertTrue(reopenedAfterDeletion.getCars().isEmpty())
        } finally {
            database.close()
            savedSelection.delete()
            directory.delete()
        }
    }
}
