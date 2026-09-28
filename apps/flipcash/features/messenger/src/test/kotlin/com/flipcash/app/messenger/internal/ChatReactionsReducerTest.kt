package com.flipcash.app.messenger.internal

import com.flipcash.services.models.chat.MessageContent
import com.flipcash.shared.chat.MessagePolicy
import com.flipcash.shared.chat.models.ChatListItem
import com.flipcash.shared.chat.reactions.ReactionStrip
import com.flipcash.shared.chat.reactions.SelfReaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The quick strip is plain state, built in the same update that selects the bubble.
 */
class ChatReactionsReducerTest {

    private val unbounded = MessagePolicy(editWindow = null, deleteWindow = null)

    private val inputs = ChatViewModel.QuickReactionInputs(
        recentStats = emptyMap(),
        catalogFill = emptyList(),
        undrawable = emptySet(),
    )

    private fun bubble(
        messageId: Long,
        canReact: Boolean = true,
        selfReactions: List<SelfReaction> = emptyList(),
    ) = ChatListItem.ContentBubble(
        messageId = messageId,
        contentIndex = 0,
        content = MessageContent.Text("hello"),
        isFromSelf = false,
        timestamp = Instant.fromEpochSeconds(1_000),
        selfReactions = selfReactions,
        canReact = canReact,
    )

    private fun reduce(
        state: ChatViewModel.State,
        event: ChatViewModel.Event,
    ): ChatViewModel.State = ChatViewModel.updateStateForEvent(event)(state)

    @Test
    fun `selecting a bubble builds the strip in the same update, highlighting the viewer's reactions`() {
        val squid = SelfReaction(emoji = "🦑", reactedAt = Instant.fromEpochSeconds(2_000))

        val state = reduce(
            ChatViewModel.State(messagePolicy = unbounded, quickReactionInputs = inputs),
            ChatViewModel.Event.ToggleMessageSelection(bubble(1, selfReactions = listOf(squid))),
        )

        assertTrue(ReactionStrip.Entry(emoji = "🦑", highlighted = true) in state.quickReactionStrip)
    }

    @Test
    fun `no strip before the inputs load, then it fills in for the selection`() {
        val selected = reduce(
            ChatViewModel.State(messagePolicy = unbounded),
            ChatViewModel.Event.ToggleMessageSelection(bubble(1)),
        )
        assertEquals(emptyList(), selected.quickReactionStrip)

        val loaded = reduce(selected, ChatViewModel.Event.QuickReactionInputsLoaded(inputs))

        assertTrue(loaded.quickReactionStrip.isNotEmpty())
    }

    @Test
    fun `no strip for a message that can't be reacted to`() {
        val state = reduce(
            ChatViewModel.State(messagePolicy = unbounded, quickReactionInputs = inputs),
            ChatViewModel.Event.ToggleMessageSelection(bubble(1, canReact = false)),
        )

        assertEquals(emptyList(), state.quickReactionStrip)
    }

    @Test
    fun `clearing the selection clears the strip`() {
        val selected = reduce(
            ChatViewModel.State(messagePolicy = unbounded, quickReactionInputs = inputs),
            ChatViewModel.Event.ToggleMessageSelection(bubble(1)),
        )

        val cleared = reduce(selected, ChatViewModel.Event.ClearMessageSelection)

        assertEquals(emptyList(), cleared.quickReactionStrip)
    }

    @Test
    fun `a reaction landing on the selected message reaches the strip`() {
        val selected = reduce(
            ChatViewModel.State(messagePolicy = unbounded, quickReactionInputs = inputs),
            ChatViewModel.Event.ToggleMessageSelection(bubble(1)),
        )
        val squid = SelfReaction(emoji = "🦑", reactedAt = Instant.fromEpochSeconds(2_000))

        val state = reduce(selected, ChatViewModel.Event.SelectionReactionsChanged(1, listOf(squid)))

        assertTrue(ReactionStrip.Entry(emoji = "🦑", highlighted = true) in state.quickReactionStrip)
    }

    @Test
    fun `a reaction on another message leaves the selection alone`() {
        val selected = reduce(
            ChatViewModel.State(messagePolicy = unbounded, quickReactionInputs = inputs),
            ChatViewModel.Event.ToggleMessageSelection(bubble(1)),
        )
        val squid = SelfReaction(emoji = "🦑", reactedAt = Instant.fromEpochSeconds(2_000))

        val state = reduce(selected, ChatViewModel.Event.SelectionReactionsChanged(2, listOf(squid)))

        assertEquals(selected, state)
    }

    @Test
    fun `a double tap selects the bubble for the strip alone`() {
        val state = reduce(
            ChatViewModel.State(messagePolicy = unbounded, quickReactionInputs = inputs),
            ChatViewModel.Event.PresentReactionStrip(bubble(1)),
        )

        assertEquals(1L, state.selection?.messageId)
        assertTrue(state.reactionStripOnly)
        assertTrue(state.quickReactionStrip.isNotEmpty())
    }

    @Test
    fun `a double tap on a message that can't be reacted to does nothing`() {
        val before = ChatViewModel.State(messagePolicy = unbounded, quickReactionInputs = inputs)

        val state = reduce(before, ChatViewModel.Event.PresentReactionStrip(bubble(1, canReact = false)))

        assertEquals(before, state)
    }

    @Test
    fun `a long-press after a double tap brings the selection bar back`() {
        val stripOnly = reduce(
            ChatViewModel.State(messagePolicy = unbounded, quickReactionInputs = inputs),
            ChatViewModel.Event.PresentReactionStrip(bubble(1)),
        )
        val cleared = reduce(stripOnly, ChatViewModel.Event.ClearMessageSelection)

        val selected = reduce(cleared, ChatViewModel.Event.ToggleMessageSelection(bubble(1)))

        assertEquals(1L, selected.selection?.messageId)
        assertFalse(selected.reactionStripOnly)
    }
}
