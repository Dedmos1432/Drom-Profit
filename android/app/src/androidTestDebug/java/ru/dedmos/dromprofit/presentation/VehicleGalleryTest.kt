package ru.dedmos.dromprofit.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.dedmos.dromprofit.presentation.components.VehicleGallery

@RunWith(AndroidJUnit4::class)
class VehicleGalleryTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun unavailableFrameKeepsOtherFramesSelectable() {
        compose.setContent {
            MaterialTheme {
                VehicleGallery(
                    imageUrls = listOf(
                        "/demo/vehicles/absent-test-image.jpg",
                        "/demo/vehicles/camry-xv50-silver.jpg",
                        "/demo/vehicles/camry-xv50-white.jpg",
                    ),
                    contentDescription = "Toyota Camry",
                )
            }
        }
        compose.onNodeWithContentDescription("Фотография 1").assertIsSelected()
        compose.onNodeWithContentDescription("Фотография 2").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("Фотография 3").performClick().assertIsSelected()
    }
}
