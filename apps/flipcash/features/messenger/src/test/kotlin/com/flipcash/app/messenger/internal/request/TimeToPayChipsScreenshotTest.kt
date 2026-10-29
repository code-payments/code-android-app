package com.flipcash.app.messenger.internal.request

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import com.flipcash.app.messenger.internal.request.NewRequestDraft.Deadline
import com.flipcash.app.theme.FlipcashPreview
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.SlidingChipRow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders Time To Pay's preset chips to `build/screenshots/timeToPay_chips_<fontScale>.png`. Only
 * the chip row: the sheet itself is a ModalBottomSheet, whose window this view-drawing harness
 * doesn't capture.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [32], qualifiers = "w411dp-h891dp-xxhdpi")
class TimeToPayChipsScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun render(fontScale: Float) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                FlipcashPreview(showBackground = true) {
                    SlidingChipRow(
                        modifier = Modifier.padding(horizontal = CodeTheme.dimens.inset),
                        options = Deadline.Preset.entries,
                        selected = Deadline.Preset.Week,
                        onSelect = {},
                        label = { stringResource(it.label) },
                    )
                }
            }
        }
        repeat(12) { composeRule.mainClock.advanceTimeByFrame() }

        val root: View = composeRule.activity.findViewById(android.R.id.content)
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val file = File(File("build/screenshots").apply { mkdirs() }, "timeToPay_chips_$fontScale.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("SCREENSHOT_WRITTEN: ${file.absolutePath}")
    }

    @Test fun defaultFont() = render(1f)

    // Android's largest font scale: "2 Weeks" and "4 Weeks" are cut to "2" and "4".
    @Test fun largestFont() = render(2f)
}

