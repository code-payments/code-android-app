package com.flipcash.app.messenger.internal

import androidx.compose.foundation.text.input.TextFieldState
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.shared.chat.MessageCapability
import com.flipcash.shared.chat.models.ChatListItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.time.Instant

/**
 * The selection bar and the composer takeover are both plain state, so the reducer is where their
 * rules live: one message selected at a time, and an edit that never costs the user their draft.
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

        assertSame(target, state.selection)
        assertEquals(target.capabilities, state.selectionCapabilities)
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

        assertSame(second, state.selection)
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
    fun `the selection is held while the delete confirmation is up`() {
        // The message stays focused behind the sheet, so raising the confirmation changes nothing.
        // Clearing it is the sheet's close — confirmed or cancelled — which the handler drives.
        val target = bubble(1)
        val selected = reduce(
            ChatViewModel.State(),
            ChatViewModel.Event.ToggleMessageSelection(target),
        )

        val state = reduce(selected, ChatViewModel.Event.DeleteMessage(target.messageId))

        assertSame(target, state.selection)
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
}
