package com.flipcash.app.messenger.internal

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.flipcash.app.messenger.internal.screens.components.SaveAllIcon
import com.flipcash.app.messenger.internal.screens.components.SavePhotoIcon
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Draws the selection bar's save icons from their real [androidx.compose.ui.graphics.vector.ImageVector]s,
 * at bar size and at 160px, into `build/screenshots/save_icons.png`, and checks that Save All's back
 * tray turns its top-right corner outward. Drawn anticlockwise, that corner bowed inward and the
 * tray read as a gear on a device.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h400dp-mdpi")
class SaveIconsScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun saveAllBackTrayCornerBowsOutward() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            Row(Modifier.background(Color.Black)) {
                // 160px at mdpi: 20/3 px per viewport unit.
                Icon(SavePhotoIcon, null, Modifier.size(160.dp), tint = Color.White)
                Icon(SaveAllIcon, null, Modifier.size(160.dp), tint = Color.White)
                Icon(SavePhotoIcon, null, Modifier.size(24.dp), tint = Color.White)
                Icon(SaveAllIcon, null, Modifier.size(24.dp), tint = Color.White)
            }
        }
        repeat(3) { composeRule.mainClock.advanceTimeByFrame() }

        val root = composeRule.activity.window.decorView.findViewById<View>(android.R.id.content)
        val bitmap = Bitmap.createBitmap(368, 160, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        File("build/screenshots").apply { mkdirs() }
            .resolve("save_icons.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        // Save All starts at x = 160. Its back tray's top-right corner runs from (17.5, 12) to
        // (20.5, 15) around a centre at (17.5, 15); the arc's midpoint is (19.62, 12.88). Bowed the
        // wrong way, the midpoint lands at (18.38, 14.12) instead.
        fun inked(x: Float, y: Float): Boolean {
            val px = bitmap.getPixel(160 + (x * SCALE).toInt(), (y * SCALE).toInt())
            return android.graphics.Color.red(px) > 128
        }
        assertTrue("outward corner is drawn", inked(19.62f, 12.88f))
        assertTrue("inward corner is not drawn", !inked(18.38f, 14.12f))
    }

    private companion object {
        const val SCALE = 160f / 24f
    }
}
