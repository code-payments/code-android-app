package com.flipcash.app.messenger.internal.invites

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.flipcash.app.theme.FlipcashPreview
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue
import java.io.File

/**
 * Renders every invite frame's preview to `build/screenshots/invite_<frame>.png`, to be compared
 * with the design by eye. Apart from 2d's preset chips, it fails only if a frame throws.
 *
 * Same mechanics as the group chrome screenshot test: pause the clock, pump a fixed number of
 * frames and draw the view directly, so a composable that keeps scheduling frames can't hang.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [32], qualifiers = "w411dp-h891dp-xxhdpi")
class InviteFramesScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun render(name: String, content: @Composable () -> Unit) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { FlipcashPreview(showBackground = true) { content() } }
        repeat(12) { composeRule.mainClock.advanceTimeByFrame() }

        val root: View = composeRule.activity.findViewById(android.R.id.content)
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val file = File(File("build/screenshots").apply { mkdirs() }, "invite_$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("SCREENSHOT_WRITTEN: ${file.absolutePath}")
    }

    @Test fun frame1m() = render("1m") { InviteFrame1mHostSheet() }
    @Test fun frame1mPrime() = render("1m-prime") { InviteFrame1mPrimeHostSheet() }
    @Test fun frame1b() = render("1b") { InviteFrame1bCreating() }
    @Test fun frame1c() = render("1c") { InviteFrame1cTooMany() }
    @Test fun frame1d() = render("1d") { InviteFrame1dJoinRequestsEmpty() }
    @Test fun frame1e() = render("1e") { InviteFrame1eNotAvailable() }
    @Test fun frame1f() = render("1f") { InviteFrame1fReady() }
    @Test fun frame1g() = render("1g") { InviteFrame1gJoin() }
    @Test fun frame2a() = render("2a") { InviteFrame2aManage() }
    @Test fun frame2b() = render("2b") { InviteFrame2bRowSheet() }
    @Test fun frame2c() = render("2c") { InviteFrame2cRevoke() }
    @Test fun frame2d() {
        render("2d") { InviteFrame2dNewLink() }
        // The spec's Uses presets are bare counts (1, 5, 10, 25), not "1 person".
        listOf("1", "5", "10", "25", "No limit").forEach {
            composeRule.onNodeWithText(it).assertExists()
        }
        composeRule.onNodeWithText("1 person").assertDoesNotExist()
        // Each row's chips share its width equally, as on iOS.
        listOf(listOf("1 day", "7 days", "30 days", "Never"), listOf("1", "5", "10", "25", "No limit"))
            .forEach { row ->
                val widths = row.map { composeRule.onNodeWithText(it).getBoundsInRoot().let { b -> b.right - b.left } }
                assertTrue(widths.max() - widths.min() <= 1.dp, "chip widths $widths")
            }
    }

    // Android's largest font scale. The chips cut a label at its word break rather than shrink it,
    // so this shows "30" and "No" for "30 days" and "No limit".
    @Test fun frame2dLargestFont() = render("2d-fontScale2") {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
            InviteFrame2dNewLink()
        }
    }

    @Test fun frame2dChosen() = render("2d-chosen") { InviteFrame2dNewLinkChosen() }
    @Test fun frame2e() = render("2e") { InviteFrame2eEmpty() }
}
