package ru.dedmos.dromprofit.data

internal const val DemoPhotoDirectory = "demo/vehicles"
private const val ServerPrefix = "/$DemoPhotoDirectory/"
private const val LegacyPrefix = "file:///android_asset/$DemoPhotoDirectory/"
private val DemoPhotoName = Regex("[a-z0-9-]+\\.jpg")
private val LegacyGallery = Regex("gallery=([0-9]{3})")

internal data class DemoPhoto(val name: String, val galleryId: String?)

internal fun parseDemoPhoto(url: String?): DemoPhoto? {
    val reference = url ?: return null
    if (reference.startsWith(ServerPrefix)) {
        val name = reference.removePrefix(ServerPrefix)
        return if (DemoPhotoName.matches(name)) DemoPhoto(name, null) else null
    }
    if (!reference.startsWith(LegacyPrefix)) return null
    val path = reference.removePrefix(LegacyPrefix)
    val name = path.substringBefore('?')
    if (!DemoPhotoName.matches(name)) return null
    val query = path.substringAfter('?', "")
    if (query.isEmpty()) return DemoPhoto(name, null)
    val gallery = LegacyGallery.matchEntire(query) ?: return null
    return DemoPhoto(name, gallery.groupValues[1])
}

internal fun demoPhotoAsset(url: String?, knownNames: Set<String>, debug: Boolean): String? {
    if (!debug) return null
    val name = parseDemoPhoto(url)?.name?.takeIf { it in knownNames } ?: return null
    return "$DemoPhotoDirectory/$name"
}
