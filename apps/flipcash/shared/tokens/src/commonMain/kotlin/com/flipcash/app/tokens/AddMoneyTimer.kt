package com.flipcash.app.tokens

import javax.inject.Inject
import kotlin.jvm.Synchronized
import kotlin.time.ComparableTimeMark
import kotlin.time.TimeSource

/**
 * Times the phases of a Coinbase add-money purchase for the `Add Money` event. [start] opens a
 * purchase when the amount is confirmed; each later [Moment] is marked once, as it happens; [finish]
 * reads the phases and closes the purchase, so a later send can't reuse its marks.
 *
 * Marks arrive from the deposit sweep's IO scope as well as the view model, so every access is
 * synchronized. The clock is monotonic; tests pass a `TestTimeSource`.
 */
class AddMoneyTimer(private val timeSource: TimeSource.WithComparableMarks) {

    @Inject constructor() : this(TimeSource.Monotonic)

    /** The moments a purchase passes through, in order. */
    enum class Moment {
        /** Coinbase returned the placed order. */
        ORDER_PLACED,

        /** Coinbase's `onramp_api.payment_authorized` webview event. */
        PAYMENT_AUTHORIZED,

        /** Coinbase's `onramp_api.polling_success` webview event. */
        PAYMENT_COMPLETED,

        /** The deposit sweep first saw a nonzero USDC balance. */
        USDC_SEEN,

        /** The USDC to USDF swap returned success. */
        SWAPPED,

        /** The USDF credit landed and the success state was shown. */
        CREDITED,
    }

    private var started: ComparableTimeMark? = null
    private val marks = mutableMapOf<Moment, ComparableTimeMark>()

    @Synchronized
    fun start() {
        marks.clear()
        started = timeSource.markNow()
    }

    /**
     * Records [moment] the first time it happens in an open purchase. The USDC sweep also runs
     * for leftover balances, so its moments only count once Coinbase has completed the payment.
     */
    @Synchronized
    fun mark(moment: Moment) {
        if (started == null) return
        if (moment >= Moment.USDC_SEEN && Moment.PAYMENT_COMPLETED !in marks) return
        if (moment !in marks) marks[moment] = timeSource.markNow()
    }

    /** The phases so far and the total up to now, or null when no purchase is open. */
    @Synchronized
    fun finish(): AddMoneyPhases? {
        val start = started ?: return null
        val now = timeSource.markNow()
        fun between(from: ComparableTimeMark?, to: ComparableTimeMark?): Long? =
            if (from != null && to != null) (to - from).inWholeMilliseconds else null

        val phases = AddMoneyPhases(
            orderTimeMillis = between(start, marks[Moment.ORDER_PLACED]),
            paymentTimeMillis = between(marks[Moment.ORDER_PLACED], marks[Moment.PAYMENT_AUTHORIZED]),
            coinbaseTimeMillis = between(marks[Moment.PAYMENT_AUTHORIZED], marks[Moment.PAYMENT_COMPLETED]),
            depositTimeMillis = between(marks[Moment.PAYMENT_COMPLETED], marks[Moment.USDC_SEEN]),
            swapTimeMillis = between(marks[Moment.USDC_SEEN], marks[Moment.SWAPPED]),
            creditTimeMillis = between(marks[Moment.SWAPPED], marks[Moment.CREDITED]),
            totalTimeMillis = (now - start).inWholeMilliseconds,
        )
        started = null
        marks.clear()
        return phases
    }
}

/** A phase is null when either of its moments never happened. */
data class AddMoneyPhases(
    val orderTimeMillis: Long?,
    val paymentTimeMillis: Long?,
    val coinbaseTimeMillis: Long?,
    val depositTimeMillis: Long?,
    val swapTimeMillis: Long?,
    val creditTimeMillis: Long?,
    val totalTimeMillis: Long,
)
