package com.flipcash.app.messenger.internal

import com.flipcash.services.models.chat.MessageContent
import com.flipcash.shared.chat.MessagePolicy
import com.flipcash.shared.chat.models.ChatListItem
import com.flipcash.shared.chat.reactions.ReactionPill
import com.flipcash.shared.chat.reactions.ReactionStrip
import com.flipcash.shared.chat.reactions.SelfReaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The quick strip and the reactors sheet are plain state: the strip is built in the same update
 * that selects the bubble, and the sheet is opened by its own event and closed by its dismissal,
 * dropping results that arrive for a sheet no longer showing.
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

    private fun pill(emoji: String, count: Long) =
        ReactionPill(emoji = emoji, count = count, selfReacted = false, pending = false)

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
    fun `opening the reactors sheet seeds its pills from the ones pressed`() {
        val pills = listOf(pill("👍", 3), pill("🔥", 1))

        val state = reduce(ChatViewModel.State(), ChatViewModel.Event.OpenReactors(1, pills))

        val reactors = assertNotNull(state.reactors)
        assertEquals(pills, reactors.pills)
        assertTrue(reactors.loading)
    }

    @Test
    fun `reactors updates for another message are dropped`() {
        val opened = reduce(ChatViewModel.State(), ChatViewModel.Event.OpenReactors(1, emptyList()))

        val state = reduce(
            opened,
            ChatViewModel.Event.ReactorsUpdated(
                messageId = 2,
                pills = listOf(pill("👍", 1)),
                rows = emptyList(),
                loading = false,
                hasMore = false,
            ),
        )

        assertEquals(opened, state)
    }

    @Test
    fun `a late dismissal of an earlier reactors sheet leaves the open one`() {
        val opened = reduce(ChatViewModel.State(), ChatViewModel.Event.OpenReactors(2, emptyList()))

        val stale = reduce(opened, ChatViewModel.Event.ReactorsDismissed(1))
        val closed = reduce(opened, ChatViewModel.Event.ReactorsDismissed(2))

        assertNotNull(stale.reactors)
        assertNull(closed.reactors)
    }
}
