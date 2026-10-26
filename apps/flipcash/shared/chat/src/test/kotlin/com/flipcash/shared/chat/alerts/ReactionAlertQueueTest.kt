package com.flipcash.shared.chat.alerts

import com.flipcash.services.models.chat.ReactionUpdate.Action
import com.flipcash.shared.chat.alerts.ReactionAlertContent.Kind
import com.flipcash.shared.chat.alerts.ReactionAlertQueue.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReactionAlertQueueTest {
    private val queue = ReactionAlertQueue()

    @Test
    fun `first event starts a reaction pill`() {
        assertEquals(Outcome.Started, queue.offer(alertEvent()))
        assertEquals(Kind.Reaction, queue.current?.kind)
        assertEquals(2_500, queue.current?.holdMillis)
    }

    @Test
    fun `boost pill holds longer and carries the delta`() {
        queue.offer(alertEvent(action = Action.BOOSTED, boostDeltaCents = 700))
        assertEquals(Kind.Boost, queue.current?.kind)
        assertEquals(700, queue.current?.boostCents)
        assertEquals(3_500, queue.current?.holdMillis)
    }

    @Test
    fun `same actor same message swaps in place`() {
        queue.offer(alertEvent(emoji = "🔥"))
        assertEquals(Outcome.Merged, queue.offer(alertEvent(emoji = "❤️", action = Action.BOOSTED, boostDeltaCents = 300)))
        val c = queue.current!!
        assertEquals(Kind.Boost, c.kind)
        assertEquals(listOf("❤️"), c.emojis)
        assertEquals(listOf<String?>("Alice"), c.actorNames)
    }

    @Test
    fun `second actor on same message becomes several`() {
        queue.offer(alertEvent())
        assertEquals(Outcome.Merged, queue.offer(alertEvent(actor = BOB, actorName = "Bob", emoji = "😂")))
        val c = queue.current!!
        assertEquals(Kind.Several, c.kind)
        assertEquals(listOf<String?>("Alice", "Bob"), c.actorNames)
        assertEquals(listOf("🔥", "😂"), c.emojis)
        assertNull(c.boostCents)
    }

    @Test
    fun `trailing emoji are capped at three and distinct`() {
        queue.offer(alertEvent(emoji = "a"))
        queue.offer(alertEvent(actor = BOB, emoji = "b"))
        queue.offer(alertEvent(actor = listOf(4), emoji = "c"))
        queue.offer(alertEvent(actor = listOf(5), emoji = "d"))
        queue.offer(alertEvent(actor = listOf(6), emoji = "a"))
        assertEquals(listOf("a", "b", "c"), queue.current!!.trailingEmojis)
        assertEquals(4, queue.current!!.emojis.size)
    }

    @Test
    fun `unnamed actors still count as distinct`() {
        queue.offer(alertEvent(actorName = null))
        queue.offer(alertEvent(actor = BOB, actorName = null))
        assertEquals(Kind.Several, queue.current!!.kind)
    }

    @Test
    fun `other message waits and advance shows it`() {
        queue.offer(alertEvent(messageId = 10))
        assertEquals(Outcome.Queued, queue.offer(alertEvent(messageId = 11, emoji = "😂")))
        assertEquals(1, queue.pendingCount)
        assertEquals(11, queue.advance()?.messageId)
        assertNull(queue.advance())
    }

    @Test
    fun `queued events for one message merge`() {
        queue.offer(alertEvent(messageId = 10))
        queue.offer(alertEvent(messageId = 11))
        queue.offer(alertEvent(messageId = 11, actor = BOB, actorName = "Bob"))
        assertEquals(1, queue.pendingCount)
        assertEquals(Kind.Several, queue.advance()?.kind)
    }

    @Test
    fun `same message id in another chat is a different message`() {
        queue.offer(alertEvent(chatId = CHAT))
        assertEquals(Outcome.Queued, queue.offer(alertEvent(chatId = OTHER_CHAT)))
    }

    @Test
    fun `clear empties everything`() {
        queue.offer(alertEvent())
        queue.offer(alertEvent(messageId = 11))
        queue.clear()
        assertNull(queue.current)
        assertEquals(0, queue.pendingCount)
    }
}
