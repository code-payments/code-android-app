package com.getcode.opencode.model.financial

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LimitsTest {

    // region Empty

    @Test
    fun `Empty has no send limits`() {
        assertNull(Limits.Empty.sendLimitFor(CurrencyCode.USD))
    }

    @Test
    fun `Empty is stale`() {
        assertTrue(Limits.Empty.isStale)
    }

    @Test
    fun `Empty has zero transacted amount`() {
        assertEquals(Fiat.Zero, Limits.Empty.amountUsdTransactedSinceConsumption)
    }

    // endregion

    // region isStale

    @Test
    fun `recently fetched limits are not stale`() {
        val limits = Limits(
            sinceDate = System.currentTimeMillis(),
            fetchDate = System.currentTimeMillis(),
            sendLimits = emptyMap(),
            amountUsdTransactedSinceConsumption = Fiat.Zero,
        )

        assertFalse(limits.isStale)
    }

    @Test
    fun `limits fetched over an hour ago are stale`() {
        val twoHoursAgo = System.currentTimeMillis() - 2 * 60 * 60 * 1000

        val limits = Limits(
            sinceDate = twoHoursAgo,
            fetchDate = twoHoursAgo,
            sendLimits = emptyMap(),
            amountUsdTransactedSinceConsumption = Fiat.Zero,
        )

        assertTrue(limits.isStale)
    }

    // endregion

    // region sendLimitFor

    @Test
    fun `sendLimitFor returns limit for existing currency`() {
        val usdLimit = SendLimit(nextTransaction = 100.0, maxPerTransaction = 500.0, maxPerDay = 1000.0)
        val limits = Limits(
            sinceDate = 0,
            fetchDate = System.currentTimeMillis(),
            sendLimits = mapOf(CurrencyCode.USD to usdLimit),
            amountUsdTransactedSinceConsumption = Fiat.Zero,
        )

        val result = limits.sendLimitFor(CurrencyCode.USD)
        assertNotNull(result)
        assertEquals(100.0, result.nextTransaction)
        assertEquals(500.0, result.maxPerTransaction)
        assertEquals(1000.0, result.maxPerDay)
    }

    @Test
    fun `sendLimitFor returns null for missing currency`() {
        val limits = Limits(
            sinceDate = 0,
            fetchDate = System.currentTimeMillis(),
            sendLimits = mapOf(
                CurrencyCode.USD to SendLimit(10.0, 50.0, 100.0)
            ),
            amountUsdTransactedSinceConsumption = Fiat.Zero,
        )

        assertNull(limits.sendLimitFor(CurrencyCode.EUR))
    }

    // endregion

    // region newInstance

    @Test
    fun `newInstance skips unknown currency codes`() {
        val sendLimits = mapOf(
            "USD" to buildSendLimit(100.0, 500.0, 1000.0),
            "INVALID_CODE" to buildSendLimit(10.0, 50.0, 100.0),
        )

        val limits = Limits.newInstance(
            sinceDate = 0,
            fetchDate = System.currentTimeMillis(),
            sendLimits = sendLimits,
            usdTransactedSinceConsumption = 50.0,
        )

        assertNotNull(limits.sendLimitFor(CurrencyCode.USD))
        // INVALID_CODE should be dropped
        assertEquals(50.0, limits.amountUsdTransactedSinceConsumption.decimalValue, 0.01)
    }

    @Test
    fun `newInstance maps multiple valid currencies`() {
        val sendLimits = mapOf(
            "USD" to buildSendLimit(100.0, 500.0, 1000.0),
            "EUR" to buildSendLimit(80.0, 400.0, 800.0),
            "CAD" to buildSendLimit(120.0, 600.0, 1200.0),
        )

        val limits = Limits.newInstance(
            sinceDate = 1000L,
            fetchDate = 2000L,
            sendLimits = sendLimits,
            usdTransactedSinceConsumption = 0.0,
        )

        assertNotNull(limits.sendLimitFor(CurrencyCode.USD))
        assertNotNull(limits.sendLimitFor(CurrencyCode.EUR))
        assertNotNull(limits.sendLimitFor(CurrencyCode.CAD))
        assertEquals(1000L, limits.sinceDate)
        assertEquals(2000L, limits.fetchDate)
    }

    // endregion

    // region SendLimit

    @Test
    fun `SendLimit Zero has all zeroes`() {
        assertEquals(0.0, SendLimit.Zero.nextTransaction)
        assertEquals(0.0, SendLimit.Zero.maxPerTransaction)
        assertEquals(0.0, SendLimit.Zero.maxPerDay)
    }

    // endregion

    companion object {
        private fun buildSendLimit(
            nextTransaction: Double,
            maxPerTransaction: Double,
            maxPerDay: Double,
        ): com.codeinc.opencode.gen.transaction.v1.TransactionService.SendLimit {
            return com.codeinc.opencode.gen.transaction.v1.sendLimit {
                this.nextTransaction = nextTransaction.toFloat()
                this.maxPerTransaction = maxPerTransaction.toFloat()
                this.maxPerDay = maxPerDay.toFloat()
            }
        }
    }
}
