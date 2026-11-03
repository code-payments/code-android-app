package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.flipcash.app.messenger.internal.pins.PinnedRow
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.Pin
import com.flipcash.shared.chat.models.ChatListItem
import com.flipcash.shared.chat.models.SenderIdentity
import com.getcode.theme.DesignSystem
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class PinnedMessagesSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val alex = listOf<Byte>(1)
    private val sentAt = Instant.fromEpochSeconds(1_000)
    private val content = MessageContent.Text("see https://flipcash.com")

    private val row = PinnedRow(
        pin = Pin(
            messageId = 7,
            pinnedBy = alex,
            pinnedAt = sentAt,
            message = ChatMessage(
                messageId = 7,
                senderId = alex,
                content = listOf(content),
                timestamp = sentAt,
                unreadSeq = 0,
                eventSequence = 7,
            ),
        ),
        bubble = ChatListItem.ContentBubble(
            messageId = 7,
            contentIndex = 0,
            content = content,
            isFromSelf = false,
            timestamp = sentAt,
        ),
        sender = SenderIdentity(userId = alex, displayName = "Alex", picture = null),
        pinnedByName = "You",
        spoken = "see https://flipcash.com",
    )

    private fun showSheet(canUnpin: Boolean, full: Boolean, onClose: () -> Unit) {
        composeTestRule.setContent {
            DesignSystem {
                PinnedMessagesSheet(
                    rows = listOf(row),
                    canUnpin = canUnpin,
                    full = full,
                    fullQuote = null,
                    onOpen = {},
                    onUnpin = {},
                    onClose = onClose,
                )
            }
        }
    }

    private fun closeWorks(canUnpin: Boolean, full: Boolean) {
        var closed = 0
        showSheet(canUnpin = canUnpin, full = full, onClose = { closed++ })

        val close = composeTestRule.onNodeWithContentDescription("Close")
        // Off-centre, near the button's corner.
        close.performTouchInput { click(Offset(width * 0.8f, height * 0.25f)) }
        assertEquals(1, closed)
        // 23dp out from its centre on each side, just inside a 48dp target, still closes.
        close.performTouchInput {
            val reach = 23.dp.toPx()
            listOf(Offset(-reach, 0f), Offset(reach, 0f), Offset(0f, -reach), Offset(0f, reach))
                .forEach { click(center + it) }
        }
        assertEquals(5, closed)

        // The title stays centred with the close beside it.
        val root = composeTestRule.onRoot().getBoundsInRoot()
        val title = composeTestRule.onNodeWithText("Pinned Messages").getBoundsInRoot()
        assertEquals(
            ((root.left + root.right) / 2).value,
            ((title.left + title.right) / 2).value,
            1f,
        )
    }

    @Test
    fun `the close pops the sheet in the normal list`() = closeWorks(canUnpin = true, full = false)

    @Test
    fun `the close pops the sheet in the read-only list`() = closeWorks(canUnpin = false, full = false)

    @Test
    fun `the close pops the sheet when the chat is full`() = closeWorks(canUnpin = true, full = true)
}
