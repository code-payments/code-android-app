package com.flipcash.app.messenger.internal

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.flipcash.app.messenger.internal.screens.components.SpeakerGateBar
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.services.models.chat.ChatRuleRequirement
import dev.chrisbanes.haze.rememberHazeState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the read-only composer panel to `build/screenshots/`. Not an assertion test.
 *
 * The blur is off: Robolectric's native runtime cannot compile the Haze shader. This shows layout,
 * outline and copy, not the blur.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w402dp-h120dp-xhdpi")
class SpeakerGateBarScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rendersNeverPanel() = render(ChatRuleRequirement.Never, "speaker_gate_never.png")

    @Test
    fun rendersCreatorPanel() = render(ChatRuleRequirement.Creator, "android-creator-composer.png")

    @Test
    fun rendersUnsupportedPanel() =
        render(ChatRuleRequirement.UnsupportedSpeakerRule, "android-unsupported-composer.png")

    private fun render(requirement: ChatRuleRequirement, fileName: String) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            FlipcashPreview(showBackground = true) {
                SpeakerGateBar(
                    requirement = requirement,
                    hazeState = rememberHazeState(),
                    currencyName = null,
                    modifier = Modifier.width(402.dp),
                    blurEnabled = false,
                )
            }
        }
        repeat(10) { composeRule.mainClock.advanceTimeByFrame() }
        val root: View = composeRule.activity.findViewById(android.R.id.content)
        val bitmap = Bitmap.createBitmap(root.width.coerceAtLeast(1), root.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val file = File("build/screenshots").apply { mkdirs() }.resolve(fileName)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("SCREENSHOT_WRITTEN: ${file.absolutePath}")
    }
}
