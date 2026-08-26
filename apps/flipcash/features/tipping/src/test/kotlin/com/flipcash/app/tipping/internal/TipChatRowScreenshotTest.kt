package com.flipcash.app.tipping.internal

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.app.tipping.internal.components.TipChatRow
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.ui.ConversationReference
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.time.Instant

/**
 * Renders the tips list row in each identity state to a PNG, so the name-or-handle rule can be
 * eyeballed without an emulator. Not an assertion test — it writes to `build/screenshots/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-xhdpi")
class TipChatRowScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rendersRowIdentityStates() {
        val at = Instant.fromEpochMilliseconds(1_700_000_000_000)
        val rows = listOf(
            // Name and handle both present: the row is one line, so only the name shows.
            ConversationReference(
                chatId = ChatId(byteArrayOf(1)),
                displayName = "Grace Hopper",
                handle = "@grace_hopper",
                lastMessagePreview = "Sent you $5.00 in Dollars",
                lastActivity = at,
            ),
            // No name — the handle stands in for it, on the same line.
            ConversationReference(
                chatId = ChatId(byteArrayOf(2)),
                displayName = null,
                handle = "@sally_streamer",
                lastMessagePreview = "Sent you $1.00 in Dollars",
                lastActivity = at,
                unreadCount = 2,
            ),
            // Neither, which is what every name-less tipper used to render as.
            ConversationReference(
                chatId = ChatId(byteArrayOf(3)),
                displayName = null,
                handle = null,
                lastMessagePreview = "Sent you $0.25 in Dollars",
                lastActivity = at,
            ),
        )

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            FlipcashPreview(showBackground = true) {
                Column(modifier = Modifier.width(360.dp).padding(vertical = 8.dp)) {
                    rows.forEach { TipChatRow(chat = it, onClick = {}) }
                }
            }
        }
        repeat(10) { composeRule.mainClock.advanceTimeByFrame() }

        capture("tip_chat_row_identity.png")
    }

    private fun capture(name: String) {
        val root: View = composeRule.activity.findViewById(android.R.id.content)
        val width = root.width.takeIf { it > 0 } ?: 1080
        val height = root.height.takeIf { it > 0 } ?: 1920
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val cropped = bitmap.trimmedToDrawnArea()

        val outDir = File("build/screenshots").apply { mkdirs() }
        val file = File(outDir, name)
        file.outputStream().use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("SCREENSHOT_WRITTEN: ${file.absolutePath} (${cropped.width}x${cropped.height})")
    }

    private fun Bitmap.trimmedToDrawnArea(): Bitmap {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (pixels[y * width + x] ushr 24 == 0) continue
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
        if (right < left || bottom < top) return this
        return Bitmap.createBitmap(this, left, top, right - left + 1, bottom - top + 1)
    }
}
