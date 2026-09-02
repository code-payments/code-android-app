package com.flipcash.shared.chat.models

import com.flipcash.services.models.chat.MessageContent
import com.flipcash.shared.chat.MessageCapability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The transcript resolves capabilities once and the bubble carries them; the selection gesture and
 * the bar's actions read that set rather than re-deriving anything from the content.
 */
class ChatListItemSelectionTest {

    private val sentAt = Instant.fromEpochSeconds(1_000)

    private fun bubble(
        content: MessageContent,
        capabilities: Set<MessageCapability>,
    ) = ChatListItem.ContentBubble(
        messageId = 42,
        contentIndex = 0,
        content = content,
        isFromSelf = true,
        timestamp = sentAt,
        capabilities = capabilities,
    )

    @Test
    fun `text exposes its body for copy and edit`() {
        val text = bubble(MessageContent.Text("hello"), setOf(MessageCapability.Copy))
        assertEquals("hello", text.plainText)
    }

    @Test
    fun `a bubble that carries no text has no body to act on`() {
        val tombstone = bubble(MessageContent.Deleted(sentAt, deletedBy = null), emptySet())
        assertNull(tombstone.plainText)
    }

    @Test
    fun `reply alone does not make a bubble selectable`() {
        // Cash resolves to Reply only, and replies have no surface yet, so a long-press here would
        // open a bar with nothing in it.
        val cash = bubble(MessageContent.Text("hello"), setOf(MessageCapability.Reply))
        assertFalse(cash.isSelectable)
    }

    @Test
    fun `any actionable capability makes a bubble selectable`() {
        val copyOnly = bubble(MessageContent.Text("hello"), setOf(MessageCapability.Copy))
        val ownMessage = bubble(
            MessageContent.Text("hello"),
            setOf(
                MessageCapability.Copy,
                MessageCapability.Reply,
                MessageCapability.Edit,
                MessageCapability.Delete,
            ),
        )

        assertTrue(copyOnly.isSelectable)
        assertTrue(ownMessage.isSelectable)
    }

    @Test
    fun `a message with nothing available is not selectable`() {
        val tombstone = bubble(MessageContent.Deleted(sentAt, deletedBy = null), emptySet())
        assertFalse(tombstone.isSelectable)
    }
}
