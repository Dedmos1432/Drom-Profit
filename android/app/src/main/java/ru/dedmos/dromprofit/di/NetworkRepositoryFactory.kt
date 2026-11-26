package ru.dedmos.dromprofit.di

import android.content.Context
import android.util.AtomicFile
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import ru.dedmos.dromprofit.BuildConfig
import ru.dedmos.dromprofit.data.NetworkCarRepository
import ru.dedmos.dromprofit.data.PrivateTextFile
import ru.dedmos.dromprofit.data.ProfitApi
import ru.dedmos.dromprofit.data.local.CarDao
import ru.dedmos.dromprofit.data.local.NetworkCarMirror
import ru.dedmos.dromprofit.data.local.sha256
import ru.dedmos.dromprofit.domain.repository.CarRepository
import java.io.File

internal fun createNetworkRepository(context: Context, cars: CarDao): CarRepository {
    val configuredUrl = BuildConfig.DROM_PROFIT_API_BASE_URL
    val baseUrl = (configuredUrl.trimEnd('/') + "/").toHttpUrlOrNull()
    if (baseUrl == null ||
        (!baseUrl.isHttps && (!BuildConfig.DEBUG || baseUrl.host !in setOf("10.0.2.2", "127.0.0.1")))
    ) {
        return NetworkCarRepository(api = null, tokenFile = null)
    }

    val client = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .build()
    val api = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .validateEagerly(true)
        .build()
        .create(ProfitApi::class.java)
    // Keep tokens private, outside backups, and scoped to the configured API host/path.
    val endpointKey = baseUrl.toString().sha256()
    val token = PrivateTextFile(AtomicFile(File(context.noBackupFilesDir, "session-" + endpointKey)))
    return NetworkCarRepository(api, token, NetworkCarMirror(cars, endpointKey))
}
