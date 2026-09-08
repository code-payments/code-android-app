package com.flipcash.app.messenger.internal

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.shared.chat.models.ChatListItem
import com.flipcash.shared.chat.ui.BubblePosition
import com.flipcash.shared.chat.ui.ContentBubble
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.time.Instant

/**
 * Renders the flash a jump leaves on the message it landed on, at three points through its fade and
 * on both bubble colours, so the scrim can be judged without an emulator. Not an assertion test —
 * it writes to `build/screenshots/`.
 *
 * Same mechanics as `ChatMessageActionScreenshotTest`: pause the clock, pump frames, draw the view.
 * The strength is passed as a constant rather than animated, because what is being looked at is the
 * scrim at a given strength, not the timing that walks through them.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h1100dp-xhdpi")
class ChatJumpAttentionScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun bubble(isFromSelf: Boolean, text: String) = ChatListItem.ContentBubble(
        messageId = 1,
        contentIndex = 0,
        content = MessageContent.Text(text),
        isFromSelf = isFromSelf,
        timestamp = Instant.fromEpochSeconds(1_000),
        capabilities = emptySet(),
    )

    @Test
    fun rendersAttentionFlashAcrossItsFade() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            FlipcashPreview(showBackground = true) {
                Column(
                    modifier = Modifier.width(360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(1f, 0.5f, 0f).forEach { strength ->
                        ContentBubble(
                            item = bubble(isFromSelf = false, text = "theirs at $strength"),
                            position = BubblePosition.Solo,
                            attention = { strength },
                        )
                        ContentBubble(
                            item = bubble(isFromSelf = true, text = "mine at $strength"),
                            position = BubblePosition.Solo,
                            attention = { strength },
                        )
                    }
                }
            }
        }
        repeat(10) { composeRule.mainClock.advanceTimeByFrame() }

        capture("chat_jump_attention.png")
    }

    private fun capture(name: String) {
        val root: View = composeRule.activity.findViewById(android.R.id.content)
        val width = root.width.takeIf { it > 0 } ?: 1080
        val height = root.height.takeIf { it > 0 } ?: 1920
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))

        val outDir = File("build/screenshots").apply { mkdirs() }
        val file = File(outDir, name)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("SCREENSHOT_WRITTEN: ${file.absolutePath} (${bitmap.width}x${bitmap.height})")
    }
}
