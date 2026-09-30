package com.flipcash.shared.chat

import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.WidgetContent
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.Mint
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
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

    // Resolved at the instant it was sent, so the default policy's fallback windows are both open
    // and this stays a statement about content rather than about age.
    @Test
    fun `own text message is copyable, editable and deletable`() {
        assertEquals(
            setOf(
                MessageCapability.Copy,
                MessageCapability.Reply,
                MessageCapability.Edit,
                MessageCapability.Delete,
            ),
            resolveCapabilities(text(), now = sentAt),
        )
    }

    @Test
    fun `someone else's text message is copyable and reportable, not editable or deletable`() {
        assertEquals(
            setOf(MessageCapability.Copy, MessageCapability.Reply, MessageCapability.Report),
            resolveCapabilities(text(isFromSelf = false)),
        )
    }

    @Test
    fun `cash is never editable or deletable, by either party`() {
        assertEquals(setOf(MessageCapability.Reply), resolveCapabilities(cash()))
        // Reportable though: a payment is something a participant sent, and it is one of the
        // things most worth reporting.
        assertEquals(
            setOf(MessageCapability.Reply, MessageCapability.Report),
            resolveCapabilities(cash(isFromSelf = false)),
        )
    }

    /**
     * Reporting your own message is left out because it is not a thing anyone does, and a row that
     * is always present is a row people stop reading. This is the first capability whose answer
     * turns on `isFromSelf` for a reason that is not about authorship privileges, so it is worth
     * pinning at both ends.
     */
    @Test
    fun `your own messages are never reportable`() {
        assertEquals(
            emptySet(),
            resolveCapabilities(text(), now = sentAt).filterTo(mutableSetOf()) {
                it == MessageCapability.Report
            },
        )
        assertEquals(emptySet(), resolveCapabilities(cash()) - MessageCapability.Reply)
    }

    @Test
    fun `nothing the server wrote, or that is already gone, can be reported`() {
        val theirTombstone = message(
            listOf(MessageContent.Deleted(sentAt, otherId)),
            isFromSelf = false,
        )
        assertEquals(emptySet(), resolveCapabilities(theirTombstone))

        val theirSystemNotice = message(
            listOf(MessageContent.System("Anna joined")),
            isFromSelf = false,
        )
        assertEquals(emptySet(), resolveCapabilities(theirSystemNotice))

    }

    private val widgetMessage
        get() = message(
            listOf(MessageContent.Widget(WidgetContent.ShareProfile("brad"))),
            isFromSelf = false,
        )

    @Test
    fun `a widget offers only Reply where the viewer may speak`() {
        // No Copy (no text), no Report (no user sender), no Edit or Delete (not the viewer's).
        assertEquals(setOf(MessageCapability.Reply), resolveCapabilities(widgetMessage))
        assertEquals(
            setOf(MessageCapability.Reply),
            resolveCapabilities(widgetMessage, canPost = true, canSpeak = true),
        )
    }

    @Test
    fun `a widget offers nothing where the speaker rules forbid speaking`() {
        assertEquals(emptySet(), resolveCapabilities(widgetMessage, canSpeak = false))
    }

    @Test
    fun `where the viewer may speak, text and cash keep Reply`() {
        assertEquals(
            setOf(MessageCapability.Copy, MessageCapability.Reply, MessageCapability.Report),
            resolveCapabilities(text(isFromSelf = false), canSpeak = true),
        )
        assertEquals(
            setOf(MessageCapability.Reply, MessageCapability.Report),
            resolveCapabilities(cash(isFromSelf = false), canSpeak = true),
        )
    }

    @Test
    fun `where the viewer may not speak, another's text loses Reply and keeps Copy and Report`() {
        assertEquals(
            setOf(MessageCapability.Copy, MessageCapability.Report),
            resolveCapabilities(text(isFromSelf = false), canSpeak = false),
        )
    }

    @Test
    fun `where the viewer may not speak, own text keeps Copy, Edit and Delete but not Reply`() {
        assertEquals(
            setOf(MessageCapability.Copy, MessageCapability.Edit, MessageCapability.Delete),
            resolveCapabilities(text(), now = sentAt, canSpeak = false),
        )
    }

    @Test
    fun `where the viewer may not speak, cash loses Reply and keeps Report`() {
        assertEquals(
            setOf(MessageCapability.Report),
            resolveCapabilities(cash(isFromSelf = false), canSpeak = false),
        )
        assertEquals(emptySet(), resolveCapabilities(cash(), canSpeak = false))
    }

    @Test
    fun `a creator-gated viewer loses Reply but keeps Copy, Report and own Edit and Delete`() {
        // The panel passes canSpeak = false for any unmet speaker rule, creator included.
        assertEquals(
            setOf(MessageCapability.Copy, MessageCapability.Report),
            resolveCapabilities(text(isFromSelf = false), canSpeak = false),
        )
        assertEquals(
            setOf(MessageCapability.Copy, MessageCapability.Edit, MessageCapability.Delete),
            resolveCapabilities(text(), now = sentAt, canSpeak = false),
        )
    }

    @Test
    fun `reactions follow the rules' reaction flag, not the posting gate`() {
        // canReact's gate is whether the rules leave reactions open (true for a creator-only block),
        // so a message stays reactable while Reply is withheld.
        assertEquals(true, canReact(text(isFromSelf = false), canSpeak = true))
        assertEquals(false, canReact(text(isFromSelf = false), canSpeak = false))
    }

    @Test
    fun `a viewer with no speaker rules, as in a direct message, is unaffected`() {
        // canSpeak defaults true: a DM has no rules to fail.
        assertEquals(
            setOf(MessageCapability.Copy, MessageCapability.Reply, MessageCapability.Report),
            resolveCapabilities(text(isFromSelf = false)),
        )
        assertEquals(true, canReact(text(isFromSelf = false)))
        assertEquals(true, canReact(cash(isFromSelf = false)))
    }

    @Test
    fun `text and cash are not reactable where the viewer may not speak`() {
        assertEquals(false, canReact(text(), canSpeak = false))
        assertEquals(false, canReact(text(isFromSelf = false), canSpeak = false))
        assertEquals(false, canReact(cash(), canSpeak = false))
        assertEquals(false, canReact(cash(isFromSelf = false), canSpeak = false))
    }

    @Test
    fun `a widget offers nothing to a viewer who cannot post`() {
        assertEquals(emptySet(), resolveCapabilities(widgetMessage, canPost = false))
    }

    @Test
    fun `reporting has no window, so an old message stays reportable`() {
        assertEquals(
            setOf(MessageCapability.Copy, MessageCapability.Reply, MessageCapability.Report),
            resolveCapabilities(
                text(isFromSelf = false),
                policy = MessagePolicy.Default,
                now = sentAt + 365.days,
            ),
        )
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
            resolveCapabilities(reply, now = sentAt),
        )
    }

    @Test
    fun `an edit window drops Edit once it lapses and leaves Delete alone`() {
        val policy = MessagePolicy(editWindow = 15.minutes, deleteWindow = null)

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

    @Test
    fun `a delete window drops Delete once it lapses and leaves Edit alone`() {
        val policy = MessagePolicy(editWindow = null, deleteWindow = 60.minutes)

        assertEquals(
            setOf(
                MessageCapability.Copy,
                MessageCapability.Reply,
                MessageCapability.Edit,
                MessageCapability.Delete,
            ),
            resolveCapabilities(text(), policy, now = sentAt + 59.minutes),
        )

        // Edit survives: this policy sets no edit window, and an unset window is no limit.
        assertEquals(
            setOf(MessageCapability.Copy, MessageCapability.Reply, MessageCapability.Edit),
            resolveCapabilities(text(), policy, now = sentAt + 61.minutes),
        )
    }

    @Test
    fun `the windows run independently, so the shorter one lapses first`() {
        val policy = MessagePolicy(editWindow = 15.minutes, deleteWindow = 60.minutes)

        assertEquals(
            setOf(MessageCapability.Copy, MessageCapability.Reply, MessageCapability.Delete),
            resolveCapabilities(text(), policy, now = sentAt + 30.minutes),
        )
        assertEquals(
            setOf(MessageCapability.Copy, MessageCapability.Reply),
            resolveCapabilities(text(), policy, now = sentAt + 90.minutes),
        )
    }

    @Test
    fun `an unset window leaves its capability open`() {
        val unbounded = MessagePolicy(editWindow = null, deleteWindow = null)

        assertEquals(
            setOf(
                MessageCapability.Copy,
                MessageCapability.Reply,
                MessageCapability.Edit,
                MessageCapability.Delete,
            ),
            resolveCapabilities(text(), unbounded, now = sentAt + 365.days),
        )
    }

    @Test
    fun `windows the server did not send fall back rather than staying open`() {
        val policy = MessagePolicy.fromFlags(editWindow = null, deleteWindow = null)

        assertEquals(MessagePolicy.FallbackEditWindow, policy.editWindow)
        assertEquals(MessagePolicy.FallbackDeleteWindow, policy.deleteWindow)
        assertEquals(policy, MessagePolicy.Default)

        assertEquals(
            setOf(MessageCapability.Copy, MessageCapability.Reply, MessageCapability.Delete),
            resolveCapabilities(text(), policy, now = sentAt + 30.minutes),
        )
        assertEquals(
            setOf(MessageCapability.Copy, MessageCapability.Reply),
            resolveCapabilities(text(), policy, now = sentAt + 365.days),
        )
    }

    /**
     * The fallback covers only what the server left unset, so a window it did send has to survive
     * the substitution — including one longer than the fallback, which is where a `?:` on the wrong
     * side of the expression would show up.
     */
    @Test
    fun `windows the server did send are used as sent`() {
        val policy = MessagePolicy.fromFlags(editWindow = 90.minutes, deleteWindow = null)

        assertEquals(90.minutes, policy.editWindow)
        assertEquals(MessagePolicy.FallbackDeleteWindow, policy.deleteWindow)

        assertEquals(
            setOf(
                MessageCapability.Copy,
                MessageCapability.Reply,
                MessageCapability.Edit,
                MessageCapability.Delete,
            ),
            resolveCapabilities(text(), policy, now = sentAt + 60.minutes),
        )
    }

    /**
     * Both numbers are maintained by hand against iOS `MessagePolicy.fallbackEditWindow` /
     * `fallbackDeleteWindow`. Nothing checks the two repos against each other, so this pins the
     * Android side: a change here fails until someone states the new value, which is the prompt to
     * go and change iOS too.
     */
    @Test
    fun `the fallback windows are the values iOS carries`() {
        assertEquals(15.minutes, MessagePolicy.FallbackEditWindow)
        assertEquals(48.hours, MessagePolicy.FallbackDeleteWindow)
    }

    /**
     * The transcript resolves once, when it is mapped; the menu re-applies the windows when it
     * opens. Both go through the same rule, so a set narrowed after the fact matches what the
     * resolver would have returned at that instant.
     */
    @Test
    fun `re-applying the windows to a resolved set matches resolving at that instant`() {
        val policy = MessagePolicy(editWindow = 15.minutes, deleteWindow = 60.minutes)
        val atSend = resolveCapabilities(text(), policy, now = sentAt)

        assertEquals(
            resolveCapabilities(text(), policy, now = sentAt + 30.minutes),
            atSend.withinWindows(sentAt, policy, now = sentAt + 30.minutes),
        )
        assertEquals(
            resolveCapabilities(text(), policy, now = sentAt + 90.minutes),
            atSend.withinWindows(sentAt, policy, now = sentAt + 90.minutes),
        )
    }

    /**
     * An eligible non-member reads a group's transcript under a Join gate. Reply, Edit and Delete
     * all post into the chat, and there is no composer for them to land in until the join does.
     */
    @Test
    fun `a viewer who cannot post keeps only copy and report`() {
        assertEquals(
            setOf(MessageCapability.Copy, MessageCapability.Report),
            resolveCapabilities(text(isFromSelf = false), canPost = false),
        )
        assertEquals(
            setOf(MessageCapability.Report),
            resolveCapabilities(cash(isFromSelf = false), canPost = false),
        )
        // Someone who left the group still has their own messages in its transcript.
        assertEquals(
            setOf(MessageCapability.Copy),
            resolveCapabilities(text(), now = sentAt, canPost = false),
        )
        assertEquals(emptySet(), resolveCapabilities(cash(), canPost = false))
    }

    @Test
    fun `narrowing a resolved set to read-only matches resolving without posting`() {
        val resolved = resolveCapabilities(text(), now = sentAt)
        assertEquals(
            resolveCapabilities(text(), now = sentAt, canPost = false),
            resolved.readOnly(),
        )
    }

    // canReact — reactions have no window and no report-only carve-out, so the only thing they
    // share with resolveCapabilities is which messages don't exist as far as an action is
    // concerned: tombstones, system notices, and unconfirmed sends.
    @Test
    fun `own and another participant's text are both reactable`() {
        assertEquals(true, canReact(text()))
        assertEquals(true, canReact(text(isFromSelf = false)))
    }

    @Test
    fun `own and another participant's cash are both reactable`() {
        assertEquals(true, canReact(cash()))
        assertEquals(true, canReact(cash(isFromSelf = false)))
    }

    @Test
    fun `an old message stays reactable, unlike edit or delete`() {
        assertEquals(true, canReact(text(isFromSelf = false)))
    }

    @Test
    fun `a tombstone is not reactable`() {
        val deleted = message(listOf(MessageContent.Deleted(sentAt, selfId)))
        assertEquals(false, canReact(deleted))
    }

    @Test
    fun `a system notice is not reactable`() {
        val system = message(listOf(MessageContent.System("Anna joined")))
        assertEquals(false, canReact(system))
    }

    @Test
    fun `a widget is reactable where the viewer may speak, and not where they may not`() {
        val widget = message(listOf(MessageContent.Widget(WidgetContent.ShareProfile("brad"))))
        assertEquals(true, canReact(widget))
        assertEquals(true, canReact(widget, canSpeak = true))
        assertEquals(false, canReact(widget, canSpeak = false))
    }

    @Test
    fun `an unconfirmed send is not reactable`() {
        assertEquals(false, canReact(text(eventSequence = 0)))
    }

    @Test
    fun `empty content is not reactable`() {
        assertEquals(false, canReact(message(emptyList())))
    }
}
