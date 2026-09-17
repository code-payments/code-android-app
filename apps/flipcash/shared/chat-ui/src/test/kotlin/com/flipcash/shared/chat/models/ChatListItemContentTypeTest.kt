package com.flipcash.shared.chat.models

import com.flipcash.services.models.chat.MessageContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.time.Instant

/**
 * Deleting a message must be an in-place update of the row already on screen. The list decides that
 * from the key and the content type, so both have to survive the swap from text to tombstone.
 */
class ChatListItemContentTypeTest {

    private val sentAt = Instant.fromEpochSeconds(1_000)

    private fun bubble(
        content: MessageContent,
        linkCard: LinkCard? = null,
    ) = ChatListItem.ContentBubble(
        messageId = 42,
        contentIndex = 0,
        content = content,
        isFromSelf = true,
        timestamp = sentAt,
        linkCard = linkCard,
    )

    private val card = LinkCard.Cash(
        url = "https://send.flipcash.com/c/#/e=KNi8pQr1n5hRU65vKJGge3",
        entropy = "KNi8pQr1n5hRU65vKJGge3",
        state = LinkCard.Cash.State.Unresolved,
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

    @Test
    fun `a carded bubble does not recycle against a plain text bubble`() {
        val plain = bubble(MessageContent.Text("https://send.flipcash.com/c/#/e=KNi8pQr1n5hRU65vKJGge3"))
        val carded = bubble(plain.content, linkCard = card)

        assertNotEquals(plain.itemContentType, carded.itemContentType)
        assertEquals("link-card-bubble", carded.itemContentType)
    }
}
