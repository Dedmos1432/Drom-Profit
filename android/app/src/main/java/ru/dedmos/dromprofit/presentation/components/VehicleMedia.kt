package ru.dedmos.dromprofit.presentation.components

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ImageNotSupported
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import ru.dedmos.dromprofit.BuildConfig
import ru.dedmos.dromprofit.data.DemoPhotoDirectory
import ru.dedmos.dromprofit.data.demoPhotoAsset
import ru.dedmos.dromprofit.data.parseDemoPhoto
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection

private const val MaxRemoteBytes = 8 * 1024 * 1024
private const val MaxDecodedPixels = 4_000_000L
private data class GalleryFrame(val url: String, val description: String)
private data class PhotoRequest(val url: String, val sampleSize: Int)
private data class PhotoResult(val request: PhotoRequest, val bitmap: Bitmap?)

private val photoCache = object : LruCache<PhotoRequest, Bitmap>(16 * 1024 * 1024) {
    override fun sizeOf(key: PhotoRequest, value: Bitmap): Int = value.byteCount
}

internal fun isDemoModelPhoto(imageUrl: String?): Boolean =
    BuildConfig.DEBUG && parseDemoPhoto(imageUrl) != null

@Composable
fun VehiclePhoto(
    imageUrl: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    VehiclePicture(imageUrl, modifier, contentDescription, ContentScale.Crop)
}

@Composable
fun VehicleGallery(
    imageUrls: List<String>,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val imageUrl = imageUrls.firstOrNull()
    val assets = LocalContext.current.assets
    val legacyFrames by key(imageUrls, assets) {
        produceState(emptyList<GalleryFrame>()) {
            if (BuildConfig.DEBUG && imageUrls.size == 1) {
                value = withContext(Dispatchers.IO) { readLegacyGallery(assets, imageUrl) }
            }
        }
    }
    val frames = legacyFrames.ifEmpty {
        imageUrls.mapIndexed { index, url -> GalleryFrame(url, "фотография " + (index + 1)) }
    }
    var selectedIndex by rememberSaveable(imageUrls) { mutableIntStateOf(0) }
    val frame = frames.getOrNull(selectedIndex)
    val mainDescription = when {
        frame == null -> contentDescription
        contentDescription == null -> frame.description
        else -> "$contentDescription, ${frame.description}"
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        VehiclePicture(
            imageUrl = frame?.url ?: imageUrl,
            modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f),
            contentDescription = mainDescription,
            contentScale = ContentScale.Fit,
        )
        if (frames.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                frames.forEachIndexed { index, thumbnail ->
                    val isSelected = selectedIndex == index
                    val border = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    }

                    VehiclePhoto(
                        imageUrl = thumbnail.url,
                        modifier = Modifier.size(96.dp, 72.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .border(if (isSelected) 2.dp else 1.dp, border, MaterialTheme.shapes.medium)
                            .semantics {
                                selected = isSelected
                                this.contentDescription = thumbnail.description.replaceFirstChar(Char::titlecase)
                            }
                            .clickable(role = Role.Button) { selectedIndex = index },
                    )
                }
            }
            if (isDemoModelPhoto(imageUrl)) {
                Text(
                    "Иллюстративные фотографии модели",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun VehiclePicture(
    imageUrl: String?,
    modifier: Modifier,
    contentDescription: String?,
    contentScale: ContentScale,
) {
    val assets = LocalContext.current.assets
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = modifier.defaultMinSize(minWidth = 48.dp, minHeight = 34.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val width = if (maxWidth == Dp.Infinity) 960 else with(density) { maxWidth.roundToPx() }
        val sampleSize = when {
            width <= 180 -> 4
            width <= 360 -> 2
            else -> 1
        }
        val request = imageUrl?.let { PhotoRequest(it, sampleSize) }
        val result by produceState<PhotoResult?>(null, request, assets) {
            value = request?.let { image ->
                PhotoResult(image, photoCache.get(image) ?: withContext(Dispatchers.IO) {
                    loadBitmap(assets, image)?.also { photoCache.put(image, it) }
                })
            }
        }
        val bitmap = result?.takeIf { it.request == request }?.bitmap
        if (bitmap == null) {
            val showLabel = maxHeight >= 64.dp
            val color = MaterialTheme.colorScheme.onSurfaceVariant
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    Icons.Outlined.ImageNotSupported,
                    contentDescription = if (showLabel) null else "Нет фотографии",
                    tint = color,
                    modifier = Modifier.size(24.dp),
                )
                if (showLabel) Text("Нет фотографии", color = color, style = MaterialTheme.typography.bodySmall)
            }
        } else {
            Image(
                bitmap.asImageBitmap(),
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun readLegacyGallery(assets: AssetManager, url: String?): List<GalleryFrame> {
    val cover = parseDemoPhoto(url) ?: return emptyList()
    val id = cover.galleryId ?: return emptyList()

    return runCatching {
        val value = assets.open(DemoPhotoDirectory + "/gallery-index.json").bufferedReader().use { it.readText() }
        val gallery = JSONObject(value).getJSONArray(id)
        require(gallery.getJSONArray(0).getString(0) == cover.name)
        List(gallery.length()) { index ->
            val photo = gallery.getJSONArray(index)
            val filename = photo.getString(0)
            GalleryFrame("/" + DemoPhotoDirectory + "/" + filename, photo.getString(1))
        }
    }.getOrDefault(emptyList())
}

private fun loadBitmap(assets: AssetManager, request: PhotoRequest): Bitmap? = try {
    val demoAsset = if (BuildConfig.DEBUG && parseDemoPhoto(request.url) != null) {
        val files = assets.list(DemoPhotoDirectory).orEmpty().toSet()
        demoPhotoAsset(request.url, files, debug = true)
    } else null
    if (demoAsset == null) {
        loadApiBitmap(request)
    } else {
        assets.open(demoAsset).use { stream ->
            BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply {
                inSampleSize = request.sampleSize
            })
        }
    }
} catch (_: IOException) {
    null
}

private fun loadApiBitmap(request: PhotoRequest): Bitmap? {
    val api = BuildConfig.DROM_PROFIT_API_BASE_URL.toHttpUrlOrNull() ?: return null
    if (!BuildConfig.DEBUG && !api.isHttps) return null
    val image = request.url.toHttpUrlOrNull() ?: return null
    // Image access is limited to the configured backend. Redirects must not escape that origin.
    if (image.scheme != api.scheme || image.host != api.host || image.port != api.port ||
        image.username.isNotEmpty() || image.password.isNotEmpty() ||
        image.host == "drom.ru" || image.host.endsWith(".drom.ru")
    ) return null

    val connection = image.toUrl().openConnection() as HttpURLConnection
    connection.instanceFollowRedirects = false
    connection.connectTimeout = 4_000
    connection.readTimeout = 5_000

    try {
        if (connection.responseCode != HttpURLConnection.HTTP_OK ||
            connection.contentType?.startsWith("image/") != true ||
            connection.contentLength > MaxRemoteBytes
        ) return null

        val bytes = connection.inputStream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size() + count > MaxRemoteBytes) return null
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = request.sampleSize
        val pixels = bounds.outWidth.toLong() * bounds.outHeight
        while (pixels / sample / sample > MaxDecodedPixels) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    } finally {
        connection.disconnect()
    }
}
