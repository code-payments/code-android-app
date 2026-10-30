package com.flipcash.shared.chat.alerts

import com.flipcash.services.models.chat.ReactionUpdate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class ReactionAlertControllerTest {

    private fun TestScope.controller(foreground: () -> Boolean = { true }) =
        ReactionAlertController(scope = this, isForeground = foreground)

    @Test
    fun `pill shows then hides after the hold`() = runTest {
        val c = controller()
        c.offer(alertEvent())
        runCurrent()
        assertNotNull(c.state.value)
        advanceTimeBy(2_501)
        assertNull(c.state.value)
        c.reset()
    }

    @Test
    fun `merge restarts the hold`() = runTest {
        val c = controller()
        c.offer(alertEvent())
        advanceTimeBy(2_000)
        c.offer(alertEvent(actor = BOB, actorName = "Bob"))
        advanceTimeBy(2_000)
        assertNotNull(c.state.value)
        assertEquals(2, c.state.value?.generation)
        advanceTimeBy(600)
        assertNull(c.state.value)
        c.reset()
    }

    @Test
    fun `next pill waits for the close animation`() = runTest {
        val c = controller()
        c.offer(alertEvent(messageId = 10))
        c.offer(alertEvent(messageId = 11))
        advanceTimeBy(2_501)
        assertNull(c.state.value)
        advanceTimeBy(ReactionAlertController.CLOSE_MILLIS + 1)
        assertEquals(11, c.state.value?.content?.messageId)
        c.reset()
    }

    @Test
    fun `background drops the event`() = runTest {
        val c = controller(foreground = { false })
        c.offer(alertEvent())
        runCurrent()
        assertNull(c.state.value)
    }

    @Test
    fun `message on screen in the open chat draws no pill`() = runTest {
        val c = controller()
        c.reportOnScreen(CHAT, setOf(10L, 11L))
        c.offer(alertEvent(messageId = 10))
        runCurrent()
        assertNull(c.state.value)
    }

    @Test
    fun `off screen message in the open chat draws no pill`() = runTest {
        val c = controller()
        c.reportOnScreen(CHAT, setOf(11L))
        c.offer(alertEvent(messageId = 10))
        runCurrent()
        assertNull(c.state.value)
    }

    @Test
    fun `reaction in another chat shows with the chat name`() = runTest {
        val c = controller()
        c.reportOnScreen(OTHER_CHAT, setOf(10L))
        c.offer(alertEvent(messageId = 10, chatId = CHAT))
        runCurrent()
        assertNotNull(c.state.value)
        assertEquals(CHAT, c.state.value?.content?.chatId)
        c.reset()
    }

    @Test
    fun `no chat open shows the pill`() = runTest {
        val c = controller()
        c.offer(alertEvent())
        runCurrent()
        assertNotNull(c.state.value)
        c.reset()
    }

    @Test
    fun `opening a chat closes its showing pill`() = runTest {
        val c = controller()
        c.offer(alertEvent())
        runCurrent()
        assertNotNull(c.state.value)
        c.reportOnScreen(CHAT, setOf(10L))
        assertNull(c.state.value)
        c.reset()
    }

    @Test
    fun `opening a chat drops its queued pills and keeps others`() = runTest {
        val c = controller()
        c.offer(alertEvent(messageId = 10, chatId = CHAT))
        c.offer(alertEvent(messageId = 11, chatId = CHAT))
        c.offer(alertEvent(messageId = 12, chatId = OTHER_CHAT))
        runCurrent()
        c.reportOnScreen(CHAT, emptySet())
        assertNull(c.state.value)
        advanceTimeBy(ReactionAlertController.CLOSE_MILLIS + 1)
        assertEquals(OTHER_CHAT, c.state.value?.content?.chatId)
        assertEquals(12, c.state.value?.content?.messageId)
        c.reset()
    }

    @Test
    fun `boost in the open chat draws no pill`() = runTest {
        val c = controller()
        c.reportOnScreen(CHAT, setOf(99L))
        c.offer(alertEvent(messageId = 10, action = ReactionUpdate.Action.BOOSTED, boostDeltaCents = 500))
        runCurrent()
        assertNull(c.state.value)
    }

    @Test
    fun `boost in another chat shows with the amount`() = runTest {
        val c = controller()
        c.reportOnScreen(OTHER_CHAT, emptySet())
        c.offer(alertEvent(messageId = 10, action = ReactionUpdate.Action.BOOSTED, boostDeltaCents = 500))
        runCurrent()
        assertEquals(ReactionAlertContent.Kind.Boost, c.state.value?.content?.kind)
        assertEquals(500L, c.state.value?.content?.boostCents)
        c.reset()
    }

    @Test
    fun `tap opens the chat at the message`() = runTest {
        val c = controller()
        val seen = mutableListOf<ReactionAlertAction>()
        backgroundScope.launch { c.actions.collect { a -> seen += a } }
        runCurrent()
        c.offer(alertEvent())
        runCurrent()
        c.onTap()
        runCurrent()
        assertEquals(listOf<ReactionAlertAction>(ReactionAlertAction.OpenChat(CHAT, 10)), seen)
        c.reset()
    }
}
