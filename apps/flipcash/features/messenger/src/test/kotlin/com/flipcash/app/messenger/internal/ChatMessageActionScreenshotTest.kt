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
import com.flipcash.app.messenger.internal.screens.components.ChatTopBar
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.shared.chat.MessageCapability
import com.flipcash.shared.chat.models.ChatListItem
import com.getcode.navigation.core.CodeNavigator
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.time.Instant

/**
 * Renders the selection bar in each capability shape to a PNG so the bar's layout can be checked
 * against WhatsApp without an emulator. Not an assertion test — it writes to `build/screenshots/`.
 *
 * Same mechanics as `ChatIdentityScreenshotTest`: pause the clock, pump a fixed number of frames,
 * and draw the Android view directly.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h1100dp-xhdpi")
class ChatMessageActionScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val sentAt = Instant.fromEpochSeconds(1_000)

    private fun bubble(
        isFromSelf: Boolean,
        capabilities: Set<MessageCapability>,
    ) = ChatListItem.ContentBubble(
        messageId = 1,
        contentIndex = 0,
        content = MessageContent.Text("hello"),
        isFromSelf = isFromSelf,
        timestamp = sentAt,
        capabilities = capabilities,
    )

    @Test
    fun rendersSelectionBarCapabilityStates() {
        val navigator = mockk<CodeNavigator>(relaxed = true)
        // Own recent message: delete inline, copy and edit under the overflow.
        val ownMessage = bubble(
            isFromSelf = true,
            capabilities = setOf(
                MessageCapability.Copy,
                MessageCapability.Reply,
                MessageCapability.Edit,
                MessageCapability.Delete,
            ),
        )
        // Someone else's message, and an own message past the edit window: overflow only, and the
        // bar has to close up rather than leave a gap where delete or edit would have been.
        val theirMessage = bubble(
            isFromSelf = false,
            capabilities = setOf(MessageCapability.Copy, MessageCapability.Reply),
        )
        val ownStaleMessage = bubble(
            isFromSelf = true,
            capabilities = setOf(
                MessageCapability.Copy,
                MessageCapability.Reply,
                MessageCapability.Delete,
            ),
        )

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            FlipcashPreview(showBackground = true) {
                Column(
                    modifier = Modifier.width(360.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    listOf(ownMessage, theirMessage, ownStaleMessage).forEach { selection ->
                        ChatTopBar(
                            navigator = navigator,
                            state = ChatViewModel.State(
                                chatType = ChatType.CONTACT_DM,
                                selection = selection,
                            ),
                            chatActionHandler = {},
                            dispatch = {},
                        )
                    }
                }
            }
        }
        repeat(10) { composeRule.mainClock.advanceTimeByFrame() }

        capture("chat_selection_bar.png")
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

    /** Crop away the untouched (fully transparent) margin so the PNG is just what was composed. */
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
