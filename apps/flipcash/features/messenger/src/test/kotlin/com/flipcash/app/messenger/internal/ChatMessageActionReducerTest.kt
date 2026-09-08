package com.flipcash.app.messenger.internal

import androidx.compose.foundation.text.input.TextFieldState
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.shared.chat.MessageCapability
import com.flipcash.shared.chat.MessagePolicy
import com.flipcash.shared.chat.models.ChatListItem
import com.flipcash.shared.chat.models.ChatQuote
import com.flipcash.shared.chat.models.ChatQuoteSnippet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The selection bar and the composer takeover are both plain state, so the reducer is where their
 * rules live: one message selected at a time, and an edit that never costs the user their draft.
 *
 * Selection is asserted by value, not identity: the reducer re-narrows the bubble's capabilities to
 * the windows open now, so what it stores is a copy of the bubble it was handed.
 */
class ChatMessageActionReducerTest {

    private val sentAt = Instant.fromEpochSeconds(1_000)

    private fun bubble(
        messageId: Long,
        text: String = "hello",
        capabilities: Set<MessageCapability> = setOf(
            MessageCapability.Copy,
            MessageCapability.Edit,
            MessageCapability.Delete,
        ),
    ) = ChatListItem.ContentBubble(
        messageId = messageId,
        contentIndex = 0,
        content = MessageContent.Text(text),
        isFromSelf = true,
        timestamp = sentAt,
        capabilities = capabilities,
    )

    private fun reduce(
        state: ChatViewModel.State,
        event: ChatViewModel.Event,
    ): ChatViewModel.State = ChatViewModel.updateStateForEvent(event)(state)

    @Test
    fun `long-pressing a bubble selects it`() {
        val target = bubble(1)

        val state = reduce(
            ChatViewModel.State(),
            ChatViewModel.Event.ToggleMessageSelection(target),
        )

        assertEquals(target, state.selection)
        assertEquals(target.capabilities, state.selectionCapabilities)
    }

    @Test
    fun `selecting a bubble past its edit window drops Edit`() {
        // The transcript resolved this bubble when it was mapped, which may have been well inside a
        // window that has since closed. Offering Edit anyway costs a round-trip the server answers
        // CANNOT_EDIT. sentAt is decades old, so any finite window here has closed.
        val state = reduce(
            ChatViewModel.State(messagePolicy = MessagePolicy(editWindow = 5.minutes)),
            ChatViewModel.Event.ToggleMessageSelection(bubble(1)),
        )

        assertEquals(
            setOf(MessageCapability.Copy, MessageCapability.Delete),
            state.selectionCapabilities,
        )
    }

    @Test
    fun `selecting a bubble past its delete window drops Delete`() {
        // Separate from the case above so a window wired to the wrong capability cannot pass both.
        val state = reduce(
            ChatViewModel.State(messagePolicy = MessagePolicy(deleteWindow = 5.minutes)),
            ChatViewModel.Event.ToggleMessageSelection(bubble(1)),
        )

        assertEquals(
            setOf(MessageCapability.Copy, MessageCapability.Edit),
            state.selectionCapabilities,
        )
    }

    @Test
    fun `long-pressing the selected bubble again clears the bar`() {
        val target = bubble(1)
        val selected = reduce(
            ChatViewModel.State(),
            ChatViewModel.Event.ToggleMessageSelection(target),
        )

        val state = reduce(selected, ChatViewModel.Event.ToggleMessageSelection(target))

        assertNull(state.selection)
        assertEquals(emptySet(), state.selectionCapabilities)
    }

    @Test
    fun `selecting another bubble replaces the selection rather than adding to it`() {
        val first = bubble(1)
        val second = bubble(2, text = "goodbye")
        val selected = reduce(
            ChatViewModel.State(),
            ChatViewModel.Event.ToggleMessageSelection(first),
        )

        val state = reduce(selected, ChatViewModel.Event.ToggleMessageSelection(second))

        assertEquals(second, state.selection)
    }

    @Test
    fun `copying clears the bar`() {
        val selected = reduce(
            ChatViewModel.State(),
            ChatViewModel.Event.ToggleMessageSelection(bubble(1)),
        )

        val state = reduce(selected, ChatViewModel.Event.CopyMessage("hello"))

        assertNull(state.selection)
    }

    @Test
    fun `the delete confirmation holds the selection but drops the focus`() {
        // The bar still has its message, so the sheet's Cancel has something to return to. The
        // focus goes because the sheet is modal — a sharp bubble behind it reads as still live.
        val target = bubble(1)
        val selected = reduce(
            ChatViewModel.State(),
            ChatViewModel.Event.ToggleMessageSelection(target),
        )

        val state = reduce(selected, ChatViewModel.Event.DeleteMessage(target.messageId))

        assertEquals(target, state.selection)
        assertTrue(state.confirmingDelete)
    }

    @Test
    fun `closing the delete confirmation returns the transcript to rest`() {
        // Confirmed or cancelled, the sheet's close is ClearMessageSelection, which the handler
        // drives. Leaving confirmingDelete set would hold the whole transcript behind the backdrop.
        val confirming = reduce(
            reduce(ChatViewModel.State(), ChatViewModel.Event.ToggleMessageSelection(bubble(1))),
            ChatViewModel.Event.DeleteMessage(1),
        )

        val state = reduce(confirming, ChatViewModel.Event.ClearMessageSelection)

        assertNull(state.selection)
        assertFalse(state.confirmingDelete)
    }

    @Test
    fun `starting an edit takes over the composer and stashes the draft`() {
        val target = bubble(1)
        val selected = reduce(
            ChatViewModel.State(chatInputState = TextFieldState("half-written")),
            ChatViewModel.Event.ToggleMessageSelection(target),
        )

        val state = reduce(
            selected,
            ChatViewModel.Event.EditMessage(target.messageId, "hello"),
        )

        assertNull(state.selection)
        val editing = assertNotNull(state.editing)
        assertEquals(1L, editing.messageId)
        assertEquals("hello", editing.originalText)
        assertEquals("half-written", editing.stashedDraft)
    }

    @Test
    fun `editing a second message keeps the original draft rather than the first edit's text`() {
        val first = reduce(
            ChatViewModel.State(chatInputState = TextFieldState("half-written")),
            ChatViewModel.Event.EditMessage(1, "hello"),
        )
        // The composer now holds the first message's body, which is not the user's draft.
        val midEdit = first.copy(chatInputState = TextFieldState("hello"))

        val state = reduce(midEdit, ChatViewModel.Event.EditMessage(2, "goodbye"))

        val editing = assertNotNull(state.editing)
        assertEquals(2L, editing.messageId)
        assertEquals("goodbye", editing.originalText)
        assertEquals("half-written", editing.stashedDraft)
    }

    @Test
    fun `ending an edit releases the composer`() {
        // Confirm, cancel and back all land here; restoring the stashed draft is the handler's job.
        val editing = reduce(
            ChatViewModel.State(chatInputState = TextFieldState("half-written")),
            ChatViewModel.Event.EditMessage(1, "hello"),
        )

        val state = reduce(editing, ChatViewModel.Event.EditingEnded)

        assertNull(state.editing)
    }

    @Test
    fun `submitting and cancelling leave the edit in place for the handler to read`() {
        val editing = reduce(
            ChatViewModel.State(),
            ChatViewModel.Event.EditMessage(1, "hello"),
        )

        assertNotNull(reduce(editing, ChatViewModel.Event.SubmitEdit).editing)
        assertNotNull(reduce(editing, ChatViewModel.Event.CancelEdit).editing)
    }

    private fun quote(messageId: Long = 4) = ChatQuote(
        messageId = messageId,
        authorName = "Ada",
        snippet = ChatQuoteSnippet.Text("the original"),
        accent = null,
    )

    @Test
    fun `replying opens the strip and clears the selection`() {
        val target = bubble(1)
        val selected = reduce(
            ChatViewModel.State(),
            ChatViewModel.Event.ToggleMessageSelection(target),
        )

        val state = reduce(selected, ChatViewModel.Event.ReplyToMessage(quote()))

        assertNull(state.selection)
        assertEquals(quote(), state.replyingTo)
    }

    /**
     * Unlike an edit, a reply leaves the composer alone: the draft is the reply. Stashing it the
     * way EditingMessage does would take the user's half-written text away at the moment they
     * decided to send it.
     */
    @Test
    fun `replying leaves the draft in the composer`() {
        val state = reduce(
            ChatViewModel.State(chatInputState = TextFieldState("half-written")),
            ChatViewModel.Event.ReplyToMessage(quote()),
        )

        assertEquals("half-written", state.chatInputState.text.toString())
    }

    @Test
    fun `starting an edit takes the reply strip down`() {
        val replying = reduce(
            ChatViewModel.State(),
            ChatViewModel.Event.ReplyToMessage(quote()),
        )

        val state = reduce(replying, ChatViewModel.Event.EditMessage(1, "hello"))

        assertNull(state.replyingTo)
        assertNotNull(state.editing)
    }

    @Test
    fun `replying takes an edit down`() {
        val editing = reduce(
            ChatViewModel.State(),
            ChatViewModel.Event.EditMessage(1, "hello"),
        )

        val state = reduce(editing, ChatViewModel.Event.ReplyToMessage(quote()))

        assertNull(state.editing)
        assertNotNull(state.replyingTo)
    }

    @Test
    fun `cancelling the reply keeps the draft`() {
        val replying = reduce(
            ChatViewModel.State(chatInputState = TextFieldState("half-written")),
            ChatViewModel.Event.ReplyToMessage(quote()),
        )

        val state = reduce(replying, ChatViewModel.Event.CancelReply)

        assertNull(state.replyingTo)
        assertEquals("half-written", state.chatInputState.text.toString())
    }

    /**
     * The send handler reads the target off state and clears the strip itself, the same way it
     * clears the composer's text. Clearing it here would empty it before the handler ran and send
     * the reply as an ordinary message: dispatchEvent reduces before it emits.
     */
    @Test
    fun `sending leaves the reply in place for the handler to read`() {
        val replying = reduce(
            ChatViewModel.State(),
            ChatViewModel.Event.ReplyToMessage(quote()),
        )

        val state = reduce(replying, ChatViewModel.Event.SendMessage)

        assertEquals(quote(), state.replyingTo)
    }
}
