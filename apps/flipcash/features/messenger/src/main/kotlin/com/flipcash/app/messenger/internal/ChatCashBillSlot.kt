package com.flipcash.app.messenger.internal

/**
 * Tracks the one received bill a chat raises for live cash, and the keyboard it took down.
 *
 * A bill is asked for before it is on screen: the entry starts a give, and the bill appears when
 * that give has a code to draw. The slot covers that gap, so a second payment arriving inside it is
 * dropped rather than starting a second give. It never covers longer than [pendingWindowMs]: a give
 * that fails before it presents never reports a bill, and must not leave the slot taken.
 *
 * The keyboard is restored on close only if it was up at the trigger, and never once the app has
 * gone away: backgrounding closes the bill, and the user returning to the chat has not asked for
 * the keyboard.
 */
internal class ChatCashBillSlot(private val pendingWindowMs: Long = 10_000L) {
    private enum class Phase { Idle, Pending, Showing }

    private var phase = Phase.Idle
    private var pendingSince = 0L
    private var keyboardHeld = false

    /** Whether a bill holds the slot, given whether the session shows one right now. */
    fun taken(sessionBillShowing: Boolean, nowMs: Long): Boolean {
        expirePending(nowMs)
        return sessionBillShowing || phase != Phase.Idle
    }

    /** The bill was asked for. [keyboardUp] is the composer keyboard at that moment. */
    fun onRequested(keyboardUp: Boolean, nowMs: Long) {
        phase = Phase.Pending
        pendingSince = nowMs
        keyboardHeld = keyboardUp
    }

    /**
     * The session's bill appeared or went away. Returns true when the composer should take the
     * keyboard back: this slot's bill closed, the keyboard was up under it, and the app is still in
     * front.
     */
    fun onSessionBill(showing: Boolean, appInForeground: Boolean, nowMs: Long): Boolean {
        expirePending(nowMs)
        when {
            showing && phase == Phase.Pending -> phase = Phase.Showing
            !showing && phase == Phase.Showing -> {
                val restore = keyboardHeld && appInForeground
                reset()
                return restore
            }
        }
        return false
    }

    /** The app left the foreground: nothing is restored when the user comes back. */
    fun onAppStopped() {
        keyboardHeld = false
    }

    private fun expirePending(nowMs: Long) {
        if (phase == Phase.Pending && nowMs - pendingSince >= pendingWindowMs) reset()
    }

    private fun reset() {
        phase = Phase.Idle
        keyboardHeld = false
    }
}
