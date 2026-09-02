package com.flipcash.shared.chat.models

import com.flipcash.services.models.chat.MessageContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * Deleting a message must be an in-place update of the row already on screen. The list decides that
 * from the key and the content type, so both have to survive the swap from text to tombstone.
 */
class ChatListItemContentTypeTest {

    private val sentAt = Instant.fromEpochSeconds(1_000)

    private fun bubble(content: MessageContent) = ChatListItem.ContentBubble(
        messageId = 42,
        contentIndex = 0,
        content = content,
        isFromSelf = true,
        timestamp = sentAt,
    )

    @Test
    fun `a tombstone keeps the key and content type of the text it replaces`() {
        val text = bubble(MessageContent.Text("hello"))
        val tombstone = bubble(MessageContent.Deleted(sentAt, deletedBy = null))

        assertEquals(text.itemKey, tombstone.itemKey)
        assertEquals(text.itemContentType, tombstone.itemContentType)
    }

    @Test
    fun `cash keeps a content type of its own`() {
        assertEquals("text-bubble", bubble(MessageContent.Text("hello")).itemContentType)
        assertEquals("system-message", bubble(MessageContent.System("joined")).itemContentType)
    }
}
