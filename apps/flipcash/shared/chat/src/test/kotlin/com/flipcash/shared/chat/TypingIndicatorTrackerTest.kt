package com.flipcash.shared.chat

import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.TypingNotification
import com.flipcash.services.models.chat.TypingState
import com.flipcash.shared.chat.internal.ChatStateHolder
import com.flipcash.shared.chat.internal.delegates.TypingIndicatorTracker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The server relays typing best-effort and never times a typist out itself, so a STOPPED that
 * never arrives must not leave the indicator up. Mirrors the iOS `ConversationController` typing
 * cases.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TypingIndicatorTrackerTest {

    private val self = listOf<Byte>(1)
    private val alice = listOf<Byte>(2)
    private val bob = listOf<Byte>(3)
    private val chat = ChatId("aabbccdd")
    private val otherChat = ChatId("eeff0011")

    private class Harness(scope: TestScope) {
        val stateHolder = ChatStateHolder()
        val tracker = TypingIndicatorTracker(
            stateHolder = stateHolder,
            timeSource = scope.testScheduler.timeSource,
            now = { Instant.fromEpochMilliseconds(scope.testScheduler.currentTime) },
        ).also { it.initialize(scope.backgroundScope) }

        fun typists(chatId: ChatId): List<ActiveTypist> =
            stateHolder.current.typingIndicators[chatId].orEmpty().sortedBy { it.since }
    }

    private fun TestScope.harness() = Harness(this)

    private fun Harness.send(chatId: ChatId, userId: List<Byte>, state: TypingState, selfId: List<Byte>? = self) =
        tracker.apply(chatId, listOf(TypingNotification(userId, state)), selfId)

    private fun TestScope.advance(duration: Duration) {
        advanceTimeBy(duration)
        runCurrent()
    }

    @Test
    fun `a typist with no further notification expires after 10 seconds`() = runTest {
        val h = harness()
        h.send(chat, alice, TypingState.STARTED_TYPING)
        runCurrent()
        assertEquals(listOf(alice), h.typists(chat).map { it.userId })

        advance(9.seconds + 999.milliseconds)
        assertEquals(listOf(alice), h.typists(chat).map { it.userId })

        advance(1.milliseconds)
        assertTrue(h.typists(chat).isEmpty())
        assertTrue(chat !in h.stateHolder.current.typingIndicators)
    }

    @Test
    fun `a STILL heartbeat extends the deadline without moving the typist`() = runTest {
        val h = harness()
        h.send(chat, alice, TypingState.STARTED_TYPING)
        advance(1.seconds)
        h.send(chat, bob, TypingState.STARTED_TYPING)
        advance(7.seconds)

        h.send(chat, alice, TypingState.STILL_TYPING)
        runCurrent()
        val startedAt = h.typists(chat).first { it.userId == alice }.since

        // Past alice's original deadline (10s), inside her refreshed one (18s).
        advance(4.seconds)
        assertEquals(listOf(alice), h.typists(chat).map { it.userId })
        assertEquals(startedAt, h.typists(chat).single().since)
        assertEquals(Instant.fromEpochMilliseconds(0), startedAt)

        advance(6.seconds)
        assertTrue(h.typists(chat).isEmpty())
    }

    @Test
    fun `a heartbeat keeps a typist ahead of a later starter`() = runTest {
        val h = harness()
        h.send(chat, alice, TypingState.STARTED_TYPING)
        advance(1.seconds)
        h.send(chat, bob, TypingState.STARTED_TYPING)
        advance(1.seconds)
        h.send(chat, alice, TypingState.STILL_TYPING)
        runCurrent()

        assertEquals(listOf(alice, bob), h.typists(chat).map { it.userId })
    }

    @Test
    fun `STOPPED and TIMED_OUT keep the typist for the linger, then drop them`() = runTest {
        val h = harness()
        h.send(chat, alice, TypingState.STARTED_TYPING)
        h.send(chat, bob, TypingState.STARTED_TYPING)
        runCurrent()

        h.send(chat, alice, TypingState.STOPPED_TYPING)
        h.send(chat, bob, TypingState.TYPING_TIMED_OUT)
        advance(799.milliseconds)
        assertEquals(listOf(alice, bob), h.typists(chat).map { it.userId })

        advance(1.milliseconds)
        assertTrue(chat !in h.stateHolder.current.typingIndicators)
    }

    @Test
    fun `a STOPPED for someone not typing adds nobody`() = runTest {
        val h = harness()
        h.send(chat, alice, TypingState.STOPPED_TYPING)
        runCurrent()
        assertTrue(chat !in h.stateHolder.current.typingIndicators)
    }

    @Test
    fun `the typist's message ends the linger at once`() = runTest {
        val h = harness()
        h.send(chat, alice, TypingState.STARTED_TYPING)
        h.send(chat, bob, TypingState.STARTED_TYPING)
        h.send(chat, alice, TypingState.STOPPED_TYPING)
        advance(100.milliseconds)

        h.tracker.messageArrived(chat, listOf(alice))
        assertEquals(listOf(bob), h.typists(chat).map { it.userId })
    }

    @Test
    fun `the signed-in user's own typing is ignored`() = runTest {
        val h = harness()
        h.send(chat, self, TypingState.STARTED_TYPING)
        runCurrent()

        assertTrue(h.typists(chat).isEmpty())
    }

    @Test
    fun `typists expire independently, across chats`() = runTest {
        val h = harness()
        h.send(chat, alice, TypingState.STARTED_TYPING)
        advance(3.seconds)
        h.send(otherChat, bob, TypingState.STARTED_TYPING)
        advance(3.seconds)
        h.send(chat, bob, TypingState.STARTED_TYPING)

        advance(4.seconds) // t=10: alice in chat lapses
        assertEquals(listOf(bob), h.typists(chat).map { it.userId })
        assertEquals(listOf(bob), h.typists(otherChat).map { it.userId })

        advance(3.seconds) // t=13: bob in otherChat lapses
        assertEquals(listOf(bob), h.typists(chat).map { it.userId })
        assertTrue(h.typists(otherChat).isEmpty())

        advance(3.seconds) // t=16: bob in chat lapses
        assertTrue(h.stateHolder.current.typingIndicators.isEmpty())
    }

    @Test
    fun `clear drops every typist and nothing re-arms`() = runTest {
        val h = harness()
        h.send(chat, alice, TypingState.STARTED_TYPING)
        h.send(otherChat, bob, TypingState.STARTED_TYPING)
        runCurrent()

        h.tracker.clear()
        assertTrue(h.stateHolder.current.typingIndicators.isEmpty())

        // A typist arriving after the clear still expires on its own deadline.
        h.send(chat, bob, TypingState.STARTED_TYPING)
        advance(10.seconds)
        assertTrue(h.stateHolder.current.typingIndicators.isEmpty())
    }
}
