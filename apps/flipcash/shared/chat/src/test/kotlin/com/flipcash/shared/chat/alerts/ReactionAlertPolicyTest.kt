package com.flipcash.shared.chat.alerts

import com.flipcash.services.models.chat.ReactionUpdate.Action
import com.flipcash.shared.chat.alerts.ReactionAlertPolicy.Decision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReactionAlertPolicyTest {

    private fun decide(
        update: com.flipcash.services.models.chat.ReactionUpdate,
        message: com.flipcash.services.models.chat.ChatMessage?,
        selfId: List<Byte>? = SELF,
    ) = ReactionAlertPolicy.decide(update, selfId, message)

    @Test
    fun `added reaction from another user on my message alerts`() {
        assertEquals(Decision.Alert, decide(reactionUpdate(), message(SELF, fromSelf = true)))
    }

    @Test
    fun `boost from another user on my message alerts`() {
        assertEquals(Decision.Alert, decide(reactionUpdate(action = Action.BOOSTED), message(SELF)))
    }

    @Test
    fun `sender id alone identifies my message`() {
        assertEquals(Decision.Alert, decide(reactionUpdate(), message(SELF, fromSelf = false)))
    }

    @Test
    fun `reaction on someone elses message is dropped`() {
        assertEquals(Decision.Drop, decide(reactionUpdate(), message(BOB)))
    }

    @Test
    fun `my own reaction from another device is dropped`() {
        assertEquals(Decision.Drop, decide(reactionUpdate(actor = SELF), message(SELF)))
    }

    @Test
    fun `removed and unknown actions are dropped`() {
        assertEquals(Decision.Drop, decide(reactionUpdate(action = Action.REMOVED), message(SELF)))
        assertEquals(Decision.Drop, decide(reactionUpdate(action = Action.UNKNOWN), message(SELF)))
    }

    @Test
    fun `uncached message asks for a fetch`() {
        assertEquals(Decision.NeedsMessage, decide(reactionUpdate(), null))
    }

    @Test
    fun `removed or own reaction never triggers a fetch`() {
        assertEquals(Decision.Drop, decide(reactionUpdate(action = Action.REMOVED), null))
        assertEquals(Decision.Drop, decide(reactionUpdate(actor = SELF), null))
    }

    @Test
    fun `signed out drops`() {
        assertEquals(Decision.Drop, decide(reactionUpdate(), message(SELF), selfId = null))
    }

    @Test
    fun `boost delta is the new total when no previous total is known`() {
        assertEquals(500, ReactionAlertPolicy.boostDeltaCents(500, null))
    }

    @Test
    fun `boost delta subtracts the actors previous total`() {
        assertEquals(700, ReactionAlertPolicy.boostDeltaCents(1200, 500))
    }

    @Test
    fun `boost delta never goes negative`() {
        assertEquals(0, ReactionAlertPolicy.boostDeltaCents(100, 500))
    }

    @Test
    fun `totals track add then boost then boost`() {
        val totals = ActorBoostTotals()
        assertNull(totals.previous(CHAT, 10, "🔥", ALICE))
        totals.record(CHAT, 10, "🔥", ALICE, 0)
        assertEquals(500, ReactionAlertPolicy.boostDeltaCents(500, totals.previous(CHAT, 10, "🔥", ALICE)))
        totals.record(CHAT, 10, "🔥", ALICE, 500)
        assertEquals(700, ReactionAlertPolicy.boostDeltaCents(1200, totals.previous(CHAT, 10, "🔥", ALICE)))
    }

    @Test
    fun `totals are per actor, emoji and message`() {
        val totals = ActorBoostTotals()
        totals.record(CHAT, 10, "🔥", ALICE, 500)
        assertNull(totals.previous(CHAT, 10, "🔥", BOB))
        assertNull(totals.previous(CHAT, 10, "❤️", ALICE))
        assertNull(totals.previous(CHAT, 11, "🔥", ALICE))
        assertNull(totals.previous(OTHER_CHAT, 10, "🔥", ALICE))
    }

    @Test
    fun `quote reads text or the reply text`() {
        assertEquals("hello", ReactionAlertPolicy.quoteOf(message(SELF)))
        assertEquals("", ReactionAlertPolicy.quoteOf(null))
    }
}
