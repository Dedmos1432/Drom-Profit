package ru.dedmos.dromprofit.data

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.dedmos.dromprofit.domain.toListingData
import ru.dedmos.dromprofit.presentation.forms.CarField
import ru.dedmos.dromprofit.presentation.forms.initialValue
import ru.dedmos.dromprofit.presentation.forms.toCarInput
import ru.dedmos.dromprofit.presentation.forms.validationErrors

class GalleryContractTest {
    private val cover = "/demo/vehicles/camry-xv50-silver.jpg"
    private val photos = listOf(cover, "/demo/vehicles/camry-xv50-white.jpg", "/demo/vehicles/camry-xv50-engine.jpg")
    private val listing = JsonParser.parseString("""
        {"source":"demo","brand":"Toyota","model":"Camry","price":1200000,"image_url":"$cover",
         "image_urls":["$cover","/demo/vehicles/camry-xv50-white.jpg","/demo/vehicles/camry-xv50-engine.jpg"]}
    """).asJsonObject.readListing()

    @Test
    fun editedDraftKeepsOrderAndLeavesPhotosUntouchedInCityPatch() {
        val original = listing.toCarInput().toCar(id = "car1")
        val initial = original.toListingData()
        val form = CarField.entries.map { initial.initialValue(it).orEmpty() }.toMutableList()
        form[CarField.CITY.ordinal] = "Владивосток"
        assertTrue(form.validationErrors(initial).isEmpty())
        val draft = form.toCarInput(initial)
        assertEquals(photos, draft.imageUrls)
        assertEquals(setOf("city"), draft.changesFrom(original).keySet())
        assertEquals(photos, draft.toJson().getAsJsonArray("image_urls").map { it.asString })

        form[CarField.IMAGE_URL.ordinal] = ""
        assertEquals(photos, form.toCarInput(initial).imageUrls)
        assertTrue(form.toCarInput(initial, clearPhotos = true).changesFrom(original).get("image_url").isJsonNull)

        form[CarField.IMAGE_URL.ordinal] = photos[1]
        assertTrue(form.validationErrors(initial).isEmpty())
        val reordered = form.toCarInput(initial)
        assertEquals(listOf(photos[1], photos[0], photos[2]), reordered.imageUrls)
        val patch = reordered.changesFrom(original)
        assertEquals(setOf("city", "image_url"), patch.keySet())
        assertEquals(photos[1], patch.get("image_url").asString)
    }

    @Test
    fun legacyCoverAndEmptyGalleryRemainSupported() {
        val input = listing.toCarInput()
        val one = input.copy(imageUrls = null).toJson()
        assertNull(one.get("image_urls"))
        assertEquals(listOf(cover), one.readListing().imageUrls)
        val arrayOnly = input.copy(imageUrl = null).toJson()
        assertEquals(cover, arrayOnly.get("image_url").asString)
        val empty = input.copy(imageUrl = null, imageUrls = emptyList()).toJson()
        assertTrue(empty.get("image_url").isJsonNull)
        assertEquals(0, empty.getAsJsonArray("image_urls").size())
        assertTrue(empty.readListing().imageUrls.isEmpty())
    }

    @Test
    fun acceptsUpToSixtyFourPhotosInWireOrder() {
        val maximum = (1..64).map { "/demo/vehicles/test-$it.jpg" }
        val draft = listing.toCarInput().copy(imageUrl = maximum.first(), imageUrls = maximum)
        assertEquals(maximum, draft.toJson().readListing().imageUrls)
        assertThrows(IllegalArgumentException::class.java) {
            draft.copy(imageUrls = maximum + "/demo/vehicles/test-65.jpg").toJson()
        }
    }

    @Test
    fun resolverOnlyUsesBundledKnownNamesInDebug() {
        val known = photos.map { it.substringAfterLast('/') }.toSet()
        assertEquals("demo/vehicles/camry-xv50-silver.jpg", demoPhotoAsset(cover, known, debug = true))
        assertNull(demoPhotoAsset(cover, known, debug = false))
        val rejected = listOf(
            "/demo/vehicles/missing.jpg",
            "/demo/vehicles/../camry-xv50-silver.jpg",
            "/demo/vehicles/%2e%2e/camry-xv50-silver.jpg",
            "/demo/vehicles\\camry-xv50-silver.jpg",
            "$cover?query=1",
            "file:///tmp/camry-xv50-silver.jpg",
            "https://example.test$cover",
        )
        for (path in rejected) assertNull(path, demoPhotoAsset(path, known, debug = true))
        assertEquals("demo/vehicles/camry-xv50-white.jpg", demoPhotoAsset(photos[1], known, debug = true))
    }
}
