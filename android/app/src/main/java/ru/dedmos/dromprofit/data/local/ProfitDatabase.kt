package ru.dedmos.dromprofit.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "cars", primaryKeys = ["scope", "id"])
internal data class CarEntity(
    val scope: String,
    val id: String,
    val position: Long,
    val source: String,
    val brand: String,
    val model: String,
    val price: Long,
    val year: Long?,
    val mileage: Long?,
    val trim: String?,
    val engineType: String?,
    val engineVolume: Double?,
    val enginePower: Long?,
    val transmission: String?,
    val driveType: String?,
    val city: String?,
    val imageUrl: String?,
    val sourceUrl: String?,
    val marketPrice: Long?,
    val listingId: String?,
    val fetchedAt: String?,
    val expensesReviewed: Boolean,
    val benefitsReviewed: Boolean,
    val expensesJson: String,
    val benefitsJson: String,
    val notesJson: String,
    val imageUrlsJson: String?
)

@Entity(tableName = "demo_selections")
internal data class DemoSelectionEntity(@PrimaryKey val scope: String)

@Dao
internal abstract class CarDao {
    @Query("SELECT * FROM cars WHERE scope = :scope ORDER BY position, id")
    abstract suspend fun selection(scope: String): List<CarEntity>

    @Query("SELECT * FROM cars WHERE scope = :scope AND id = :id")
    abstract suspend fun car(scope: String, id: String): CarEntity?

    @Query("SELECT MAX(position) FROM cars WHERE scope = :scope")
    protected abstract suspend fun lastPosition(scope: String): Long?

    @Query("SELECT MIN(position) FROM cars WHERE scope = :scope")
    protected abstract suspend fun firstPosition(scope: String): Long?

    @Query("DELETE FROM cars WHERE scope = :scope AND id = :id")
    abstract suspend fun deleteCar(scope: String, id: String): Int

    @Query("DELETE FROM cars WHERE scope = :scope")
    abstract suspend fun clearSelection(scope: String)

    @Query("SELECT EXISTS (SELECT 1 FROM demo_selections WHERE scope = :scope)")
    abstract suspend fun isInitialized(scope: String): Boolean

    @Insert
    protected abstract suspend fun markInitialized(selection: DemoSelectionEntity)

    @Upsert
    protected abstract suspend fun upsert(car: CarEntity)

    @Upsert
    protected abstract suspend fun upsertAll(cars: List<CarEntity>)

    @Transaction
    open suspend fun initializeDemo(scope: String, cars: List<CarEntity>) {
        if (isInitialized(scope)) return
        upsertAll(cars)
        markInitialized(DemoSelectionEntity(scope))
    }

    @Transaction
    open suspend fun saveCar(car: CarEntity): CarEntity {
        val previous = this.car(car.scope, car.id)
        val position = previous?.position ?: (lastPosition(car.scope) ?: -1L) + 1
        upsert(car.withRememberedGallery(previous).copy(position = position))
        return requireNotNull(this.car(car.scope, car.id))
    }

    @Transaction
    open suspend fun prependCar(car: CarEntity): CarEntity {
        val first = firstPosition(car.scope)
        upsert(car.copy(position = first?.minus(1L) ?: 0L))
        return requireNotNull(this.car(car.scope, car.id))
    }

    @Transaction
    open suspend fun replaceSelection(scope: String, cars: List<CarEntity>): List<CarEntity> {
        val previous = selection(scope).associateBy(CarEntity::id)
        clearSelection(scope)
        upsertAll(cars.map { it.withRememberedGallery(previous[it.id]) })
        return selection(scope)
    }

    @Transaction
    open suspend fun fillEmptySelection(scope: String, samples: List<CarEntity>): List<CarEntity> {
        val existing = selection(scope)
        if (existing.isNotEmpty()) return existing
        upsertAll(samples)
        return selection(scope)
    }
}

@Database(entities = [CarEntity::class, DemoSelectionEntity::class], version = 2, exportSchema = false)
internal abstract class ProfitDatabase : RoomDatabase() {
    abstract fun carDao(): CarDao
}

internal val ProfitDatabaseMigration1To2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE cars ADD COLUMN imageUrlsJson TEXT")
    }
}

// A short server response carries only the cover. Keep a matching cached gallery.
private fun CarEntity.withRememberedGallery(previous: CarEntity?): CarEntity {
    if (imageUrlsJson != null || imageUrl != previous?.imageUrl) return this
    return copy(imageUrlsJson = previous?.imageUrlsJson)
}
