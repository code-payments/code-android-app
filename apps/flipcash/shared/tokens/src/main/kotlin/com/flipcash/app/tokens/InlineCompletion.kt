package com.flipcash.app.tokens

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Timing for completing a transaction in place, on the screen that submitted it, instead of
 * sending the user to the processing interstitial.
 *
 * Landing is usually sub-second now, so the confirm button spins while the completion signal is
 * polled every [FAST_POLL_INTERVAL] for up to [FAST_POLL_WINDOW]. If nothing terminal arrives in
 * that window the flow falls back to the processing screen, which keeps polling at the regular
 * [SLOW_POLL_INTERVAL] for what is left of the original [TOTAL_SWAP_BUDGET] / [TOTAL_BALANCE_BUDGET].
 *
 * These numbers are the cross-platform parity spec; iOS carries the same values.
 */
object InlineCompletion {
    val FAST_POLL_INTERVAL: Duration = 250.milliseconds

    /** The longest the submitting screen waits in place before handing off to Processing. */
    val FAST_POLL_WINDOW: Duration = 5.seconds

    /** How long the button's success state shows before the flow exits. */
    val SUCCESS_HOLD: Duration = 1.seconds

    val SLOW_POLL_INTERVAL: Duration = 1.seconds

    /** Total swap polling budget, in [SLOW_POLL_INTERVAL] ticks (unchanged from before). */
    const val TOTAL_SWAP_BUDGET = 90

    /** Total currency-launch balance polling budget, in [SLOW_POLL_INTERVAL] ticks (unchanged). */
    const val TOTAL_BALANCE_BUDGET = 300

    val FAST_POLL_ATTEMPTS: Int = (FAST_POLL_WINDOW / FAST_POLL_INTERVAL).toInt()

    /** Slow-path attempts left once the fast window is spent, so the total stays ~unchanged. */
    val SWAP_SLOW_ATTEMPTS: Int =
        TOTAL_SWAP_BUDGET - (FAST_POLL_WINDOW / SLOW_POLL_INTERVAL).toInt()
    val BALANCE_SLOW_ATTEMPTS: Int =
        TOTAL_BALANCE_BUDGET - (FAST_POLL_WINDOW / SLOW_POLL_INTERVAL).toInt()
}
