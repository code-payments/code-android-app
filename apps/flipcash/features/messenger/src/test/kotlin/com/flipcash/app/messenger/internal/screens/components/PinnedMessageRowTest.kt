package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
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
class PinnedMessageRowTest {

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

    // The merged tree, as accessibility reads it. The bubble's own text and link still sit in the
    // unmerged tree under the cover's clearAndSetSemantics.
    private val clickable = SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)

    private class RecordingUriHandler : UriHandler {
        val opened = mutableListOf<String>()
        override fun openUri(uri: String) {
            opened += uri
        }
    }

    @Test
    fun `only the bubble and Unpin take a click`() {
        var opened = 0
        var unpinned = 0
        composeTestRule.setContent {
            DesignSystem {
                PinnedMessageRow(row = row, canUnpin = true, onOpen = { opened++ }, onUnpin = { unpinned++ })
            }
        }

        val clicks = composeTestRule.onAllNodes(clickable)
            .fetchSemanticsNodes()
            .map { it.config.getOrNull(SemanticsProperties.TestTag) }
        assertEquals(listOf("pinned_bubble_7", "action_unpin_7"), clicks)

        composeTestRule.onNodeWithTag("pinned_bubble_7").performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.onNodeWithTag("action_unpin_7").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(1, opened)
        assertEquals(1, unpinned)
    }

    @Test
    fun `the bubble is labelled as the jump and reads its message`() {
        composeTestRule.setContent {
            DesignSystem {
                PinnedMessageRow(row = row, canUnpin = true, onOpen = {}, onUnpin = {})
            }
        }

        val bubble = composeTestRule.onNodeWithTag("pinned_bubble_7").fetchSemanticsNode()
        assertEquals("Jump to message", bubble.config[SemanticsActions.OnClick].label)
        assertEquals(listOf("see https://flipcash.com"), bubble.config[SemanticsProperties.ContentDescription])
        // The bubble's own text is not a second node: the cover speaks for it.
        composeTestRule.onAllNodesWithText("see https://flipcash.com")
            .fetchSemanticsNodes()
            .let { assertEquals(0, it.size) }
    }

    @Test
    fun `a real tap fires only on the bubble and Unpin`() {
        var opened = 0
        var unpinned = 0
        val uris = RecordingUriHandler()
        composeTestRule.setContent {
            CompositionLocalProvider(LocalUriHandler provides uris) {
                DesignSystem {
                    PinnedMessageRow(row = row, canUnpin = true, onOpen = { opened++ }, onUnpin = { unpinned++ })
                }
            }
        }

        composeTestRule.onNodeWithText("Alex").performTouchInput { click() }
        composeTestRule.onNodeWithText("Pinned by You", substring = true).performTouchInput { click() }
        assertEquals(0, opened)
        assertEquals(0, unpinned)

        // Off-centre, near the bubble's corner and Unpin's edge.
        composeTestRule.onNodeWithTag("pinned_bubble_7").performTouchInput { click(topLeft + Offset(4f, 4f)) }
        composeTestRule.onNodeWithTag("action_unpin_7").performTouchInput { click(centerRight - Offset(2f, 0f)) }
        assertEquals(1, opened)
        assertEquals(1, unpinned)

        // On the link inside the bubble: the jump, not the link.
        composeTestRule.onNodeWithTag("pinned_bubble_7").performTouchInput { click(center) }
        assertEquals(2, opened)
        assertEquals(emptyList<String>(), uris.opened)
    }

    @Test
    fun `without the right to unpin, the bubble is the only click`() {
        composeTestRule.setContent {
            DesignSystem {
                PinnedMessageRow(row = row, canUnpin = false, onOpen = {}, onUnpin = {})
            }
        }

        val clicks = composeTestRule.onAllNodes(clickable).fetchSemanticsNodes()
        assertEquals(1, clicks.size)
    }
}
