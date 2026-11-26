package ru.dedmos.dromprofit.data.local

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class GalleryRoomMigrationTest {
    @Test
    fun keepsOldCarsAndPreservesFullGalleryAcrossShortResponsesAndDatabaseReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "gallery-migration-" + UUID.randomUUID() + ".db"
        val path = context.getDatabasePath(name)
        val cover = "/demo/vehicles/camry-xv50-silver.jpg"
        val photos = listOf(cover, "/demo/vehicles/camry-xv50-white.jpg", "/demo/vehicles/camry-xv50-engine.jpg")
        path.parentFile?.mkdirs()
        try {
            SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
                old.execSQL("""
                    CREATE TABLE cars (
                        scope TEXT NOT NULL, id TEXT NOT NULL, position INTEGER NOT NULL,
                        source TEXT NOT NULL, brand TEXT NOT NULL, model TEXT NOT NULL, price INTEGER NOT NULL,
                        year INTEGER, mileage INTEGER, trim TEXT, engineType TEXT, engineVolume REAL,
                        enginePower INTEGER, transmission TEXT, driveType TEXT, city TEXT, imageUrl TEXT,
                        sourceUrl TEXT, marketPrice INTEGER, listingId TEXT, fetchedAt TEXT,
                        expensesReviewed INTEGER NOT NULL, benefitsReviewed INTEGER NOT NULL,
                        expensesJson TEXT NOT NULL, benefitsJson TEXT NOT NULL, notesJson TEXT NOT NULL,
                        PRIMARY KEY(scope, id)
                    )
                """.trimIndent())
                old.execSQL("CREATE TABLE demo_selections (scope TEXT NOT NULL PRIMARY KEY)")
                old.execSQL("INSERT INTO demo_selections VALUES ('scope')")
                old.execSQL("""
                    INSERT INTO cars (scope, id, position, source, brand, model, price, imageUrl,
                                      expensesReviewed, benefitsReviewed, expensesJson, benefitsJson, notesJson)
                    VALUES ('scope', 'old', 7, 'DEMO', 'Toyota', 'Camry', 1200000, ?, 1, 0, '[]', '[]', '[]')
                """.trimIndent(), arrayOf(cover))
                old.version = 1
            }
            fun open() = Room.databaseBuilder(context, ProfitDatabase::class.java, name)
                .addMigrations(ProfitDatabaseMigration1To2).build()

            open().use { db ->
                val dao = db.carDao()
                assertTrue(dao.isInitialized("scope"))
                val old = requireNotNull(dao.car("scope", "old")).toDomain()
                assertEquals(listOf(cover), old.imageUrls)
                assertEquals(1_200_000L, old.price)
                assertTrue(old.expensesReviewed)
                dao.saveCar(old.copy(imageUrls = photos).toEntity("scope"))

                val short = old.copy(city = "Владивосток").toEntity("scope", hasGallery = false)
                dao.saveCar(short)
                dao.replaceSelection("scope", listOf(short))
            }
            open().use { db ->
                val dao = db.carDao()
                val reopened = requireNotNull(dao.car("scope", "old")).toDomain()
                assertEquals("Владивосток", reopened.city)
                assertEquals(photos, reopened.imageUrls)
                val cleared = reopened.copy(imageUrl = null, imageUrls = emptyList()).toEntity("scope")
                dao.saveCar(cleared)
                assertTrue(requireNotNull(dao.car("scope", "old")).toDomain().imageUrls.isEmpty())
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
