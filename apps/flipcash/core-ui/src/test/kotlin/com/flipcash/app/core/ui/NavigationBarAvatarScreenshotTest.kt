package com.flipcash.app.core.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.flipcash.app.core.navigation.NavBarButton
import com.flipcash.app.theme.FlipcashPreview
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the You tab's photo slot in both selection states to a PNG so the ring can be eyeballed
 * against node 9713:664 without an emulator. Not an assertion test — it captures to
 * `build/screenshots/`, and scales the capture up so a 1dp/2dp ring difference is visible.
 *
 * Frames are pumped on a paused clock rather than waiting for idle, like
 * [TokenCardWatermarkScreenshotTest]: the bar's selection animations keep scheduling frames.
 *
 * Left at the default mdpi, so a dp is a pixel and the ring measures against the design's px
 * values directly.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class NavigationBarAvatarScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rendersYouTabPhotoStates() {
        val photo: @Composable (Modifier) -> Unit = { modifier ->
            Box(modifier.background(Color(0xFF8E6E5B)))
        }
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            FlipcashPreview(showBackground = true) {
                Column(
                    modifier = Modifier
                        .width(360.dp)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // You unselected, with a photo — the state this test exists for.
                    NavigationBar(
                        state = rememberNavigationBarState(selectedTab = NavBarButton.Wallet),
                        avatar = photo,
                    )
                    // You selected, with a photo.
                    NavigationBar(
                        state = rememberNavigationBarState(selectedTab = NavBarButton.TipCard),
                        avatar = photo,
                    )
                    // No photo — the glyph, for contrast.
                    NavigationBar(
                        state = rememberNavigationBarState(selectedTab = NavBarButton.Wallet),
                    )
                }
            }
        }
        repeat(20) { composeRule.mainClock.advanceTimeByFrame() }

        val root: View = composeRule.activity.findViewById(android.R.id.content)
        val width = root.width.takeIf { it > 0 } ?: 1080
        val height = root.height.takeIf { it > 0 } ?: 1920
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        // Nearest-neighbour upscale: ring widths are 1-2px at this density, and smoothing them
        // would hide exactly what the capture is for.
        val scaled = Bitmap.createScaledBitmap(bitmap, width * 3, height * 3, false)

        val outDir = File("build/screenshots").apply { mkdirs() }
        val file = File(outDir, "nav_bar_you_photo_states.png")
        file.outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("SCREENSHOT_WRITTEN: ${file.absolutePath} (${width}x$height)")
    }
}
