package com.flipcash.app.tokens

import com.flipcash.app.tokens.AddMoneyTimer.Moment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource

class AddMoneyTimerTest {

    private val timeSource = TestTimeSource()
    private val timer = AddMoneyTimer(timeSource)

    @Test
    fun `each phase runs from the previous moment to its own`() {
        timer.start()
        Moment.entries.forEachIndexed { i, moment ->
            timeSource += ((i + 1) * 100).milliseconds
            timer.mark(moment)
        }
        timeSource += 50.milliseconds

        assertEquals(
            AddMoneyPhases(
                orderTimeMillis = 100,
                paymentTimeMillis = 200,
                coinbaseTimeMillis = 300,
                depositTimeMillis = 400,
                swapTimeMillis = 500,
                creditTimeMillis = 600,
                totalTimeMillis = 2_150,
            ),
            timer.finish(),
        )
    }

    @Test
    fun `a phase whose start never happened is absent`() {
        timer.start()
        timeSource += 100.milliseconds
        timer.mark(Moment.ORDER_PLACED)
        timeSource += 100.milliseconds
        // payment_authorized never arrived.
        timer.mark(Moment.PAYMENT_COMPLETED)
        timeSource += 100.milliseconds
        timer.mark(Moment.USDC_SEEN)

        assertEquals(
            AddMoneyPhases(
                orderTimeMillis = 100,
                paymentTimeMillis = null,
                coinbaseTimeMillis = null,
                depositTimeMillis = 100,
                swapTimeMillis = null,
                creditTimeMillis = null,
                totalTimeMillis = 300,
            ),
            timer.finish(),
        )
    }

    @Test
    fun `a moment keeps its first mark`() {
        timer.start()
        timeSource += 100.milliseconds
        timer.mark(Moment.ORDER_PLACED)
        timeSource += 100.milliseconds
        timer.mark(Moment.ORDER_PLACED)

        assertEquals(100, timer.finish()?.orderTimeMillis)
    }

    @Test
    fun `sweep moments before Coinbase completes the payment are ignored`() {
        timer.start()
        timer.mark(Moment.ORDER_PLACED)
        // A sweep of leftover USDC, not this purchase's deposit.
        timer.mark(Moment.USDC_SEEN)
        timer.mark(Moment.SWAPPED)
        timeSource += 100.milliseconds
        timer.mark(Moment.PAYMENT_AUTHORIZED)
        timer.mark(Moment.PAYMENT_COMPLETED)
        timeSource += 100.milliseconds
        timer.mark(Moment.USDC_SEEN)

        val phases = timer.finish()
        assertEquals(100, phases?.depositTimeMillis)
        assertNull(phases?.swapTimeMillis)
    }

    @Test
    fun `finish closes the purchase`() {
        timer.start()
        timer.finish()

        timer.mark(Moment.ORDER_PLACED)
        assertNull(timer.finish())
    }

    @Test
    fun `marks before start are ignored`() {
        timer.mark(Moment.ORDER_PLACED)
        timer.start()

        assertNull(timer.finish()?.orderTimeMillis)
    }

    @Test
    fun `start drops an earlier purchase's marks`() {
        timer.start()
        timer.mark(Moment.ORDER_PLACED)
        timeSource += 100.milliseconds
        timer.start()

        assertNull(timer.finish()?.orderTimeMillis)
    }
}
