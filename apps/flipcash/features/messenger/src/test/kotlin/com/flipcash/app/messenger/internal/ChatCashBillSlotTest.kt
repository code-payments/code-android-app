package com.flipcash.app.messenger.internal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatCashBillSlotTest {

    private fun slot() = ChatCashBillSlot(pendingWindowMs = 10_000L)

    @Test
    fun `keyboard up at the trigger is restored when the bill closes`() {
        val s = slot()
        s.onRequested(keyboardUp = true, nowMs = 0)
        assertFalse(s.onSessionBill(showing = true, appInForeground = true, nowMs = 100))
        assertTrue(s.onSessionBill(showing = false, appInForeground = true, nowMs = 5_000))
    }

    @Test
    fun `every close route restores the same way`() {
        // Put in Wallet, swipe, scrim, back and the 50 s timeout all end in the session's bill
        // going away; the slot sees only that.
        repeat(5) {
            val s = slot()
            s.onRequested(keyboardUp = true, nowMs = 0)
            s.onSessionBill(showing = true, appInForeground = true, nowMs = 10)
            assertTrue(s.onSessionBill(showing = false, appInForeground = true, nowMs = 50_000))
        }
    }

    @Test
    fun `keyboard down at the trigger stays down`() {
        val s = slot()
        s.onRequested(keyboardUp = false, nowMs = 0)
        s.onSessionBill(showing = true, appInForeground = true, nowMs = 10)
        assertFalse(s.onSessionBill(showing = false, appInForeground = true, nowMs = 20))
    }

    @Test
    fun `backgrounding while the bill is up restores nothing on return`() {
        val s = slot()
        s.onRequested(keyboardUp = true, nowMs = 0)
        s.onSessionBill(showing = true, appInForeground = true, nowMs = 10)
        s.onAppStopped()
        assertFalse(s.onSessionBill(showing = false, appInForeground = true, nowMs = 20))
    }

    @Test
    fun `a bill closing after the app left restores nothing even if the stop was missed`() {
        val s = slot()
        s.onRequested(keyboardUp = true, nowMs = 0)
        s.onSessionBill(showing = true, appInForeground = true, nowMs = 10)
        assertFalse(s.onSessionBill(showing = false, appInForeground = false, nowMs = 20))
    }

    @Test
    fun `a bill that is not this slots does not restore the keyboard`() {
        val s = slot()
        assertFalse(s.onSessionBill(showing = true, appInForeground = true, nowMs = 0))
        assertFalse(s.onSessionBill(showing = false, appInForeground = true, nowMs = 1))
    }

    @Test
    fun `a second payment is dropped while the first bill is pending or showing`() {
        val s = slot()
        assertFalse(s.taken(sessionBillShowing = false, nowMs = 0))
        s.onRequested(keyboardUp = false, nowMs = 0)
        // Pending: the give has started but the session has no bill yet.
        assertTrue(s.taken(sessionBillShowing = false, nowMs = 500))
        s.onSessionBill(showing = true, appInForeground = true, nowMs = 600)
        assertTrue(s.taken(sessionBillShowing = true, nowMs = 700))
    }

    @Test
    fun `the first bill closing frees the slot and the dropped payment does not come back`() {
        val s = slot()
        s.onRequested(keyboardUp = false, nowMs = 0)
        s.onSessionBill(showing = true, appInForeground = true, nowMs = 10)
        assertTrue(s.taken(true, 20))
        s.onSessionBill(showing = false, appInForeground = true, nowMs = 30)
        // Free again for a payment that arrives later; nothing was queued behind the first.
        assertFalse(s.taken(false, 40))
    }

    @Test
    fun `a give that never presents frees the slot and restores nothing`() {
        val s = slot()
        s.onRequested(keyboardUp = true, nowMs = 0)
        assertTrue(s.taken(false, 9_999))
        assertFalse(s.taken(false, 10_000))
        // A later, unrelated bill closing must not raise the keyboard.
        s.onSessionBill(showing = true, appInForeground = true, nowMs = 20_000)
        assertFalse(s.onSessionBill(showing = false, appInForeground = true, nowMs = 21_000))
    }
}
