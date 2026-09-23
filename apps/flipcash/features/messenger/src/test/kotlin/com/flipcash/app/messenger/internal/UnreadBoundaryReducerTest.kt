package com.flipcash.app.messenger.internal

import com.flipcash.shared.chat.UnreadBoundary
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The boundary is resolved once per visit and then only the lifetime policy may clear it. Reading
 * the chat must not move it: the list advances the READ pointer as the reader scrolls, and a
 * boundary that followed would slide the divider away from what was new at open.
 */
class UnreadBoundaryReducerTest {

    private val at = UnreadBoundary.At(readThrough = 10, count = 30)

    private fun reduce(
        state: ChatViewModel.State,
        event: ChatViewModel.Event,
        lifetime: UnreadDividerLifetime = UnreadDividerLifetime.UntilClose,
    ): ChatViewModel.State = ChatViewModel.stateReducer(event, lifetime)(state)

    @Test
    fun `a chat opens resolving`() = assertEquals(UnreadBoundary.Resolving, ChatViewModel.State().unreadBoundary)

    @Test
    fun `resolution sets the boundary and the walk budget`() {
        val state = reduce(ChatViewModel.State(), ChatViewModel.Event.UnreadBoundaryResolved(at, walkBudget = 30))

        assertEquals(at, state.unreadBoundary)
        assertEquals(30, state.unreadWalkBudget)
    }

    @Test
    fun `reading does not move the boundary`() {
        val state = reduce(ChatViewModel.State(unreadBoundary = at), ChatViewModel.Event.AdvanceReadPointer(40))

        assertEquals(at, state.unreadBoundary)
    }

    @Test
    fun `until close keeps the divider through a send`() {
        val state = reduce(ChatViewModel.State(unreadBoundary = at), ChatViewModel.Event.SendMessage)

        assertEquals(at, state.unreadBoundary)
    }

    @Test
    fun `until send clears the divider on a send`() {
        val state = reduce(
            ChatViewModel.State(unreadBoundary = at),
            ChatViewModel.Event.SendMessage,
            UnreadDividerLifetime.UntilSend,
        )

        assertEquals(UnreadBoundary.None, state.unreadBoundary)
    }

    @Test
    fun `until send leaves a boundary that is still resolving`() {
        val state = reduce(
            ChatViewModel.State(),
            ChatViewModel.Event.SendMessage,
            UnreadDividerLifetime.UntilSend,
        )

        assertEquals(UnreadBoundary.Resolving, state.unreadBoundary)
    }

    @Test
    fun `the shipped lifetime is until close`() =
        assertEquals(UnreadDividerLifetime.UntilClose, UNREAD_DIVIDER_LIFETIME)
}
