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
import ru.dedmos.dromprofit.domain.ExpenseInput
import ru.dedmos.dromprofit.domain.ExpenseKind
import ru.dedmos.dromprofit.domain.NoteInput
import ru.dedmos.dromprofit.domain.NoteKind
import java.io.File
import java.nio.file.Files

@RunWith(AndroidJUnit4::class)
class DemoCarRoomTest {
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
        } finally {
            database.close()
            savedSelection.delete()
            directory.delete()
        }
    }
}
