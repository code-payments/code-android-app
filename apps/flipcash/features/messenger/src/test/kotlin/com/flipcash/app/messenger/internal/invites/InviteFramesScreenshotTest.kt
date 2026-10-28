package com.flipcash.app.messenger.internal.invites

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.flipcash.app.theme.FlipcashPreview
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders every invite frame's preview to `build/screenshots/invite_<frame>.png`, to be compared
 * with the design by eye. Not an assertion test; it fails only if a frame throws.
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

    @Test fun frame1a() = render("1a") { InviteFrame1aHostSheet() }
    @Test fun frame1b() = render("1b") { InviteFrame1bCreating() }
    @Test fun frame1c() = render("1c") { InviteFrame1cTooMany() }
    @Test fun frame1d() = render("1d") { InviteFrame1dJoinRequestsEmpty() }
    @Test fun frame1e() = render("1e") { InviteFrame1eNotAvailable() }
    @Test fun frame1f() = render("1f") { InviteFrame1fReady() }
    @Test fun frame1g() = render("1g") { InviteFrame1gJoin() }
    @Test fun frame2a() = render("2a") { InviteFrame2aManage() }
    @Test fun frame2b() = render("2b") { InviteFrame2bRowSheet() }
    @Test fun frame2c() = render("2c") { InviteFrame2cRevoke() }
    @Test fun frame2d() = render("2d") { InviteFrame2dNewLink() }
    @Test fun frame2e() = render("2e") { InviteFrame2eEmpty() }
}
