package com.flipcash.shared.chat

import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.MessageContent
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.Mint
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
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

    private val everything = setOf(
        MessageCapability.Copy,
        MessageCapability.Reply,
        MessageCapability.Edit,
        MessageCapability.Delete,
    )

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

    /**
     * Resolves at send time by default. Every window is open at that instant, so the table tests
     * below assert content and authorship without the clock entering into it; the window tests
     * pass their own [now].
     */
    private fun capabilities(
        message: ChatMessage,
        policy: MessagePolicy = MessagePolicy.Default,
        now: Instant = sentAt,
    ) = resolveCapabilities(message, policy, now)

    @Test
    fun `own text message is copyable, editable and deletable`() {
        assertEquals(everything, capabilities(text()))
    }

    @Test
    fun `someone else's text message is copyable but not editable or deletable`() {
        assertEquals(
            setOf(MessageCapability.Copy, MessageCapability.Reply),
            capabilities(text(isFromSelf = false)),
        )
    }

    @Test
    fun `cash is never editable or deletable, by either party`() {
        assertEquals(setOf(MessageCapability.Reply), capabilities(cash()))
        assertEquals(setOf(MessageCapability.Reply), capabilities(cash(isFromSelf = false)))
    }

    @Test
    fun `a tombstone offers nothing`() {
        val deleted = message(listOf(MessageContent.Deleted(sentAt, selfId)))
        assertEquals(emptySet(), capabilities(deleted))
    }

    @Test
    fun `an unconfirmed message offers nothing`() {
        // `expected_event_sequence` is validated `>= 1`, so no valid edit or delete request exists
        // for a message the server has not acknowledged yet.
        assertEquals(emptySet(), capabilities(text(eventSequence = 0)))
    }

    @Test
    fun `system notices are not a participant's message`() {
        val system = message(listOf(MessageContent.System("Anna joined")))
        assertEquals(emptySet(), capabilities(system))
    }

    @Test
    fun `empty content offers nothing`() {
        assertEquals(emptySet(), capabilities(message(emptyList())))
    }

    @Test
    fun `a reply counts as text, so it stays copyable and editable`() {
        val reply = message(
            listOf(MessageContent.Reply(repliedMessageId = 7, content = listOf(MessageContent.Text("hi")))),
        )
        assertEquals(everything, capabilities(reply))
    }

    // --- Windows ---

    @Test
    fun `an edit window drops Edit once it lapses and leaves Delete alone`() {
        val policy = MessagePolicy(editWindow = 15.minutes, deleteWindow = null)

        assertEquals(everything, capabilities(text(), policy, now = sentAt + 14.minutes))

        assertEquals(
            setOf(MessageCapability.Copy, MessageCapability.Reply, MessageCapability.Delete),
            capabilities(text(), policy, now = sentAt + 16.minutes),
        )
    }

    @Test
    fun `a delete window drops Delete once it lapses`() {
        val policy = MessagePolicy(editWindow = null, deleteWindow = 48.hours)

        assertEquals(everything, capabilities(text(), policy, now = sentAt + 47.hours))

        assertEquals(
            setOf(MessageCapability.Copy, MessageCapability.Reply, MessageCapability.Edit),
            capabilities(text(), policy, now = sentAt + 49.hours),
        )
    }

    @Test
    fun `both windows lapsing leaves only what anyone may do`() {
        val policy = MessagePolicy(editWindow = 15.minutes, deleteWindow = 48.hours)

        assertEquals(
            setOf(MessageCapability.Copy, MessageCapability.Reply),
            capabilities(text(), policy, now = sentAt + 49.hours),
        )
    }

    @Test
    fun `a message at exactly the window length is still actionable`() {
        // Inclusive on both windows, and iOS matches. A message resolved at the boundary instant
        // keeps its actions; it loses them one tick later.
        val policy = MessagePolicy(editWindow = 15.minutes, deleteWindow = 48.hours)

        assertEquals(everything, capabilities(text(), policy, now = sentAt + 15.minutes))
        assertEquals(
            setOf(MessageCapability.Copy, MessageCapability.Reply, MessageCapability.Delete),
            capabilities(text(), policy, now = sentAt + 15.minutes + 1.seconds),
        )

        assertEquals(
            setOf(MessageCapability.Copy, MessageCapability.Reply, MessageCapability.Delete),
            capabilities(text(), policy, now = sentAt + 48.hours),
        )
        assertEquals(
            setOf(MessageCapability.Copy, MessageCapability.Reply),
            capabilities(text(), policy, now = sentAt + 48.hours + 1.seconds),
        )
    }

    @Test
    fun `a null window means no limit`() {
        // Only reachable by naming it. `from` never produces one, so this is the shape a test or a
        // deliberate opt-out gets, not anything the server can hand us.
        val unlimited = MessagePolicy(editWindow = null, deleteWindow = null)

        assertEquals(everything, capabilities(text(), unlimited, now = sentAt + 365.days))
    }

    @Test
    fun `the fallback windows are what iOS applies`() {
        // Guards the half of parity that is a value rather than a branch: the two platforms have to
        // substitute the same durations for the same message to resolve the same capability set.
        assertEquals(15.minutes, MessagePolicy.FallbackEditWindow)
        assertEquals(48.hours, MessagePolicy.FallbackDeleteWindow)
    }

    @Test
    fun `unset server windows fall back rather than leaving the action open`() {
        // What `UserFlags.Default` produces: the fetch failed, or the server sent neither field.
        // Both arrive as null and both get the client's window, so the flags being absent gates
        // the menu instead of opening it.
        val fallback = MessagePolicy.from(editWindow = null, deleteWindow = null)

        assertEquals(everything, capabilities(text(), fallback, now = sentAt + 15.minutes))
        assertEquals(
            setOf(MessageCapability.Copy, MessageCapability.Reply, MessageCapability.Delete),
            capabilities(text(), fallback, now = sentAt + 15.minutes + 1.seconds),
        )
        assertEquals(
            setOf(MessageCapability.Copy, MessageCapability.Reply),
            capabilities(text(), fallback, now = sentAt + 48.hours + 1.seconds),
        )
    }

    @Test
    fun `a window the server does publish wins over the fallback`() {
        val policy = MessagePolicy.from(editWindow = 1.hours, deleteWindow = 2.hours)

        // Past the 15-minute fallback but inside the server's hour.
        assertEquals(everything, capabilities(text(), policy, now = sentAt + 30.minutes))
        // Past the server's windows, well short of the 48-hour delete fallback.
        assertEquals(
            setOf(MessageCapability.Copy, MessageCapability.Reply),
            capabilities(text(), policy, now = sentAt + 3.hours),
        )
    }

    @Test
    fun `an unset window falls back independently of one the server did publish`() {
        val policy = MessagePolicy.from(editWindow = 1.hours, deleteWindow = null)

        assertEquals(1.hours, policy.editWindow)
        assertEquals(MessagePolicy.FallbackDeleteWindow, policy.deleteWindow)
    }

    @Test
    fun `the default policy is the fallback policy`() {
        // A call site that forgets to pass a policy is gated, not unbounded.
        assertEquals(MessagePolicy.FallbackEditWindow, MessagePolicy.Default.editWindow)
        assertEquals(MessagePolicy.FallbackDeleteWindow, MessagePolicy.Default.deleteWindow)
    }

    @Test
    fun `windows never grant a capability the message does not have`() {
        val policy = MessagePolicy(editWindow = null, deleteWindow = null)

        // Someone else's message stays theirs however open the windows are, and cash stays cash.
        assertEquals(
            setOf(MessageCapability.Copy, MessageCapability.Reply),
            capabilities(text(isFromSelf = false), policy),
        )
        assertEquals(setOf(MessageCapability.Reply), capabilities(cash(), policy))
    }
}
