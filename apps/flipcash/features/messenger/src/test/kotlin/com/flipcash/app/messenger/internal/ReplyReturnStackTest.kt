package com.flipcash.app.messenger.internal

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The return stack behind the jump-to-bottom button: quote taps push the message they were tapped
 * in, the button walks back through them newest first, and scrolling down past one by hand drops
 * it. iOS keeps the same stack by the same rules, so a change here is a change there too.
 */
class ReplyReturnStackTest {

    private fun reduce(state: ChatViewModel.State, event: ChatViewModel.Event): ChatViewModel.State =
        ChatViewModel.stateReducer(event, UnreadDividerLifetime.UntilClose)(state)

    private fun withStack(vararg origins: Long) = ChatViewModel.State(returnStack = origins.toList())

    @Test
    fun `a chat opens with nothing to return to`() = assertEquals(emptyList(), ChatViewModel.State().returnStack)

    @Test
    fun `a quote tap pushes the message it was tapped in`() {
        val state = reduce(withStack(10), ChatViewModel.Event.JumpToMessage(messageId = 3, origin = 20))

        assertEquals(listOf(10L, 20L), state.returnStack)
    }

    @Test
    fun `a second tap in the message on top is not pushed again`() {
        val state = reduce(withStack(10, 20), ChatViewModel.Event.JumpToMessage(messageId = 4, origin = 20))

        assertEquals(listOf(10L, 20L), state.returnStack)
    }

    @Test
    fun `a tap in a message lower down is pushed again`() {
        val state = reduce(withStack(10, 20), ChatViewModel.Event.JumpToMessage(messageId = 4, origin = 10))

        assertEquals(listOf(10L, 20L, 10L), state.returnStack)
    }

    @Test
    fun `a message link landing clears the stack`() {
        val state = reduce(withStack(10, 20), ChatViewModel.Event.JumpToMessage(messageId = 4))

        assertEquals(emptyList(), state.returnStack)
    }

    @Test
    fun `the button returns to the origin on top`() {
        val state = reduce(
            withStack(10, 20),
            ChatViewModel.Event.ReturnResolved(ReturnLanding(origin = 20, budget = 6, popped = 1)),
        )

        assertEquals(listOf(10L), state.returnStack)
        assertEquals(20L, state.jumpTarget)
        assertEquals(6, state.jumpBudget)
        assertEquals(0, state.newestRequests)
    }

    @Test
    fun `the button takes gone origins off with the one it returns to`() {
        val state = reduce(
            withStack(10, 20, 30),
            ChatViewModel.Event.ReturnResolved(ReturnLanding(origin = 10, budget = 9, popped = 3)),
        )

        assertEquals(emptyList(), state.returnStack)
        assertEquals(10L, state.jumpTarget)
    }

    @Test
    fun `with every origin gone the button goes to the bottom`() {
        val state = reduce(
            withStack(10, 20),
            ChatViewModel.Event.ReturnResolved(ReturnLanding(origin = null, budget = null, popped = 2)),
        )

        assertEquals(emptyList(), state.returnStack)
        assertEquals(null, state.jumpTarget)
        assertEquals(1, state.newestRequests)
    }

    @Test
    fun `the landing skips origins no longer in the chat`() = runTest {
        val stored = mapOf(10L to 9)

        assertEquals(
            ReturnLanding(origin = 10, budget = 9, popped = 3),
            listOf(10L, 20L, 30L).returnLanding { stored[it] },
        )
    }

    @Test
    fun `the landing stops at the top origin when it is still there`() = runTest {
        assertEquals(
            ReturnLanding(origin = 30, budget = 1, popped = 1),
            listOf(10L, 20L, 30L).returnLanding { 1 },
        )
    }

    @Test
    fun `the landing finds nothing when every origin is gone`() = runTest {
        assertEquals(
            ReturnLanding(origin = null, budget = null, popped = 2),
            listOf(10L, 20L).returnLanding { null },
        )
    }

    // reverseLayout: index 0 is the newest row, and a higher index is further up the transcript.

    @Test
    fun `an origin on screen or above it has been reached`() {
        val index = mapOf(10L to 2, 20L to 5, 30L to 8)

        assertEquals(setOf(20L, 30L), listOf(10L, 20L, 30L).originsReached(firstVisibleIndex = 5) { index[it] })
    }

    @Test
    fun `an origin not loaded has not been reached`() {
        assertEquals(emptySet(), listOf(10L).originsReached(firstVisibleIndex = 0) { null })
    }

    @Test
    fun `scrolling past origins drops the ones on top`() {
        val state = reduce(withStack(10, 20, 30), ChatViewModel.Event.ReturnOriginsReached(setOf(20, 30)))

        assertEquals(listOf(10L), state.returnStack)
    }

    @Test
    fun `a reached origin under one still waiting keeps its place`() {
        val state = reduce(withStack(10, 20, 30), ChatViewModel.Event.ReturnOriginsReached(setOf(10, 20)))

        assertEquals(listOf(10L, 20L, 30L), state.returnStack)
    }

    @Test
    fun `reaching the bottom clears the stack`() {
        val state = reduce(withStack(10, 20), ChatViewModel.Event.ReturnStackCleared)

        assertEquals(emptyList(), state.returnStack)
    }

    @Test
    fun `sending a message clears the stack`() {
        val state = reduce(withStack(10, 20), ChatViewModel.Event.SendMessage)

        assertEquals(emptyList(), state.returnStack)
    }
}
