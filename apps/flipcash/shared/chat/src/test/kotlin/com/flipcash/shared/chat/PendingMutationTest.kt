package com.flipcash.shared.chat

import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.MessageContent
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.time.Instant

/**
 * The optimistic overlay: what the transcript shows between a mutation being sent and the server
 * answering. The overlay is never written to the database, so "rolling back" is dropping it —
 * which these tests express as [ChatMessage.applying] with a null mutation.
 */
class PendingMutationTest {

    private val selfId = listOf<Byte>(1, 2, 3)
    private val sentAt = Instant.fromEpochSeconds(1_000)
    private val mutatedAt = Instant.fromEpochSeconds(2_000)

    private fun message(
        content: List<MessageContent> = listOf(MessageContent.Text("before")),
        eventSequence: Long = 4,
    ) = ChatMessage(
        messageId = 1,
        senderId = selfId,
        content = content,
        timestamp = sentAt,
        unreadSeq = 0,
        eventSequence = eventSequence,
        isFromSelf = true,
    )

    private fun edit(expectedSequence: Long = 4, text: String = "after") = PendingMutation(
        messageId = 1,
        expectedSequence = expectedSequence,
        kind = PendingMutation.Kind.Edited(text, mutatedAt),
    )

    private fun delete(expectedSequence: Long = 4) = PendingMutation(
        messageId = 1,
        expectedSequence = expectedSequence,
        kind = PendingMutation.Kind.Deleted(mutatedAt, selfId),
    )

    @Test
    fun `an edit swaps the body and marks the message edited`() {
        val overlaid = message().applying(edit())

        assertEquals(listOf(MessageContent.Text("after")), overlaid.content)
        assertEquals(mutatedAt, overlaid.lastEditedTs)
    }

    @Test
    fun `editing a reply keeps its citation instead of flattening it`() {
        val reply = message(
            listOf(MessageContent.Reply(repliedMessageId = 7, content = listOf(MessageContent.Text("before")))),
        )

        val overlaid = reply.applying(edit())

        assertEquals(
            listOf(MessageContent.Reply(repliedMessageId = 7, content = listOf(MessageContent.Text("after")))),
            overlaid.content,
        )
    }

    @Test
    fun `a delete replaces the body with a tombstone`() {
        val overlaid = message().applying(delete())

        assertEquals(listOf(MessageContent.Deleted(mutatedAt, selfId)), overlaid.content)
    }

    @Test
    fun `the overlay still applies at the sequence it was written against`() {
        val overlaid = message(eventSequence = 4).applying(edit(expectedSequence = 4))

        assertEquals(listOf(MessageContent.Text("after")), overlaid.content)
    }

    @Test
    fun `a newer stored row retires the overlay`() {
        // The server's version has landed. Persisting before dropping the overlay is safe precisely
        // because this check hands the row over the moment it is newer.
        val stored = message(eventSequence = 5)

        val overlaid = stored.applying(edit(expectedSequence = 4))

        assertSame(stored, overlaid)
    }

    @Test
    fun `dropping the mutation restores the stored message`() {
        val stored = message()

        assertSame(stored, stored.applying(null))
        assertEquals(listOf(MessageContent.Text("before")), stored.applying(null).content)
        assertNull(stored.applying(null).lastEditedTs)
    }
}
