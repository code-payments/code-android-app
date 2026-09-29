package com.flipcash.shared.chat.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.flipcash.app.theme.FlipcashTheme
import com.getcode.theme.CodeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the bubble for a message this client can't read, with each hint, to a PNG for
 * comparison with the design (node 10416:1576). Not an assertion test — it writes to
 * `build/screenshots/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w402dp-h400dp-xhdpi")
class UndecryptableBubbleScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rendersUndecryptableBubble() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            FlipcashTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(CodeTheme.colors.background)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    UndecryptableBubble(hint = UndecryptableHint.UpdateApp)
                    UndecryptableBubble(hint = UndecryptableHint.AskToResend("Grace"))
                    UndecryptableBubble(hint = UndecryptableHint.TrySendingAgain)
                }
            }
        }
        repeat(10) { composeRule.mainClock.advanceTimeByFrame() }

        val root: View = composeRule.activity.findViewById(android.R.id.content)
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val outDir = File("build/screenshots").apply { mkdirs() }
        val file = File(outDir, "undecryptable_bubble.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("SCREENSHOT_WRITTEN: ${file.absolutePath} (${bitmap.width}x${bitmap.height})")
    }
}
