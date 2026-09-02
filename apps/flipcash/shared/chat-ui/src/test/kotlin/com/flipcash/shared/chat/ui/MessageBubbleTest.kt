package com.flipcash.shared.chat.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.shared.chat.models.ChatListItem
import com.getcode.theme.DesignSystem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Instant

/**
 * What a deleted or edited message actually renders. The tombstone copy depends on who deleted it,
 * and the "Edited" marker has to reach the accessibility tree — it is drawn pinned to the bubble
 * corner rather than laid out after the text, so it would be easy to lose.
 */
@RunWith(RobolectricTestRunner::class)
class MessageBubbleTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sentAt = Instant.fromEpochSeconds(1_000)

    private fun bubble(
        content: MessageContent,
        isEdited: Boolean = false,
        deletedByViewer: Boolean = false,
    ) = ChatListItem.ContentBubble(
        messageId = 42,
        contentIndex = 0,
        content = content,
        isFromSelf = true,
        timestamp = sentAt,
        isEdited = isEdited,
        deletedByViewer = deletedByViewer,
    )

    private fun setBubble(item: ChatListItem.ContentBubble) {
        composeTestRule.setContent {
            DesignSystem {
                ContentBubble(item = item, position = BubblePosition.Solo)
            }
        }
    }

    @Test
    fun `a message the viewer deleted says so`() {
        setBubble(bubble(MessageContent.Deleted(sentAt, deletedBy = listOf<Byte>(1, 2, 3)), deletedByViewer = true))

        composeTestRule.onNodeWithText("You deleted this message").assertIsDisplayed()
    }

    @Test
    fun `a message someone else deleted is attributed to no one`() {
        setBubble(bubble(MessageContent.Deleted(sentAt, deletedBy = listOf<Byte>(4, 5, 6))))

        composeTestRule.onNodeWithText("This message was deleted").assertIsDisplayed()
    }

    @Test
    fun `an edited message shows the marker alongside its body`() {
        setBubble(bubble(MessageContent.Text("hello"), isEdited = true))

        // The body carries the marker's layout reservation as a trailing placeholder character,
        // so its text is "hello" plus that one character.
        composeTestRule.onNodeWithText("hello", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Edited").assertIsDisplayed()
    }

    @Test
    fun `an unedited message has no marker`() {
        setBubble(bubble(MessageContent.Text("hello")))

        composeTestRule.onNodeWithText("hello").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Edited").assertCountEquals(0)
    }
}
