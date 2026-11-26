package ru.dedmos.dromprofit.data.local

import android.database.sqlite.SQLiteException
import kotlinx.coroutines.CancellationException
import ru.dedmos.dromprofit.domain.Car
import java.security.MessageDigest

internal class NetworkCarMirror(private val cars: CarDao, private val endpointKey: String) {
    suspend fun replace(authorization: String, selection: List<Car>): List<Car> = withMirror(selection) {
        val scope = scope(authorization)
        val rows = selection.mapIndexed { index, car -> car.toEntity(scope, index.toLong()) }
        cars.replaceSelection(scope, rows).map(CarEntity::toDomain)
    }

    suspend fun save(authorization: String, car: Car): Car = withMirror(car) {
        cars.saveCar(car.toEntity(scope(authorization))).toDomain()
    }

    suspend fun invalidate(authorization: String, carId: String) = withMirror(Unit) {
        cars.deleteCar(scope(authorization), carId)
        Unit
    }

    suspend fun clear(authorization: String) = withMirror(Unit) {
        cars.clearSelection(scope(authorization))
    }

    private fun scope(authorization: String): String = "api/" + endpointKey + "/" + authorization.sha256()

    // Server responses stay usable if the local mirror fails. Never read an older snapshot here.
    private suspend fun <T> withMirror(authoritative: T, operation: suspend () -> T): T = try {
        operation()
    } catch (error: CancellationException) {
        throw error
    } catch (_: SQLiteException) {
        authoritative
    } catch (_: IllegalStateException) {
        authoritative
    }
}

internal fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }
