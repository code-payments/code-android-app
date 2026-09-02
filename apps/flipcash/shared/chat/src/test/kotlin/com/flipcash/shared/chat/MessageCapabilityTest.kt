package com.flipcash.shared.chat

import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.MessageContent
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.Mint
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The capability table, resolved once so no menu site has to re-derive it. Group roles land here
 * later as another input to [resolveCapabilities], which is why these assertions are written
 * against the returned set rather than against any particular menu.
 */
@RunWith(RobolectricTestRunner::class)
class MessageCapabilityTest {

    private val selfId = listOf<Byte>(1, 2, 3)
    private val otherId = listOf<Byte>(4, 5, 6)
    private val mint = Mint("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAaaaaaaaaaaa")
    private val sentAt = Instant.fromEpochSeconds(1_000)

    private fun message(
        content: List<MessageContent>,
        isFromSelf: Boolean = true,
        eventSequence: Long = 4,
    ) = ChatMessage(
        messageId = 1,
        senderId = if (isFromSelf) selfId else otherId,
        content = content,
        timestamp = sentAt,
        unreadSeq = 0,
        eventSequence = eventSequence,
        isFromSelf = isFromSelf,
    )

    private fun text(isFromSelf: Boolean = true, eventSequence: Long = 4) =
        message(listOf(MessageContent.Text("hello")), isFromSelf, eventSequence)

    private fun cash(isFromSelf: Boolean = true) = message(
        listOf(
            MessageContent.Cash(
                intentId = listOf<Byte>(9),
                amount = Fiat(quarks = 100L),
                mint = mint,
            ),
        ),
        isFromSelf,
    )

    @Test
    fun `own text message is copyable, editable and deletable`() {
        assertEquals(
            setOf(
                MessageCapability.Copy,
                MessageCapability.Reply,
                MessageCapability.Edit,
                MessageCapability.Delete,
            ),
            resolveCapabilities(text()),
        )
    }

    @Test
    fun `someone else's text message is copyable but not editable or deletable`() {
        assertEquals(
            setOf(MessageCapability.Copy, MessageCapability.Reply),
            resolveCapabilities(text(isFromSelf = false)),
        )
    }

    @Test
    fun `cash is never editable or deletable, by either party`() {
        assertEquals(setOf(MessageCapability.Reply), resolveCapabilities(cash()))
        assertEquals(setOf(MessageCapability.Reply), resolveCapabilities(cash(isFromSelf = false)))
    }

    @Test
    fun `a tombstone offers nothing`() {
        val deleted = message(listOf(MessageContent.Deleted(sentAt, selfId)))
        assertEquals(emptySet(), resolveCapabilities(deleted))
    }

    @Test
    fun `an unconfirmed message offers nothing`() {
        // `expected_event_sequence` is validated `>= 1`, so no valid edit or delete request exists
        // for a message the server has not acknowledged yet.
        assertEquals(emptySet(), resolveCapabilities(text(eventSequence = 0)))
    }

    @Test
    fun `system notices are not a participant's message`() {
        val system = message(listOf(MessageContent.System("Anna joined")))
        assertEquals(emptySet(), resolveCapabilities(system))
    }

    @Test
    fun `empty content offers nothing`() {
        assertEquals(emptySet(), resolveCapabilities(message(emptyList())))
    }

    @Test
    fun `a reply counts as text, so it stays copyable and editable`() {
        val reply = message(
            listOf(MessageContent.Reply(repliedMessageId = 7, content = listOf(MessageContent.Text("hi")))),
        )
        assertEquals(
            setOf(
                MessageCapability.Copy,
                MessageCapability.Reply,
                MessageCapability.Edit,
                MessageCapability.Delete,
            ),
            resolveCapabilities(reply),
        )
    }

    @Test
    fun `an edit window drops Edit once it lapses and leaves Delete alone`() {
        val policy = MessagePolicy(editWindow = 15.minutes)

        assertEquals(
            setOf(
                MessageCapability.Copy,
                MessageCapability.Reply,
                MessageCapability.Edit,
                MessageCapability.Delete,
            ),
            resolveCapabilities(text(), policy, now = sentAt + 14.minutes),
        )

        assertEquals(
            setOf(MessageCapability.Copy, MessageCapability.Reply, MessageCapability.Delete),
            resolveCapabilities(text(), policy, now = sentAt + 16.minutes),
        )
    }
}
