package com.getcode.opencode.model.financial

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Fiat-domain port of iOS's `LaunchpadSellFeeTests`. Where iOS asserts on token
 * quarks, we assert on the fiat value (Android computes the fee in fiat, not
 * token quarks — the pool deducts the real fee on-chain).
 */
class LaunchpadSellFeeTest {

    @Test
    fun `fee is bps of the amount`() {
        val gross = Fiat(20.20)

        val fee = gross.launchpadSellFee(bps = 100)

        // 1% of 20.20 = 0.202 → "$0.20"
        assertEquals(gross.quarks / 100, fee.quarks)
        assertEquals("$0.20", fee.formatted())
    }

    @Test
    fun `a sub-cent fee displays as zero`() {
        val tiny = Fiat(0.00005)

        val fee = tiny.launchpadSellFee(bps = 100)

        // Android rounds fiat micro-units rather than flooring token quarks
        // (iOS's model), but a sub-threshold fee still shows as $0.00.
        assertEquals("$0.00", fee.formatted())
        assertFalse(fee.hasDisplayableValue)
    }

    @Test
    fun `grossing up nets back to the original after the fee`() {
        assertEquals("$20.20", Fiat(20.0).grossingUpLaunchpadSellFee(bps = 100).formatted())
        assertEquals("$10.10", Fiat(10.0).grossingUpLaunchpadSellFee(bps = 100).formatted())
        assertEquals("$0.01", Fiat(0.01).grossingUpLaunchpadSellFee(bps = 100).formatted())
    }

    @Test
    fun `buy maximum shape - full balance nets to balance times one minus fee`() {
        val balance = Fiat(20.0)

        val fee = balance.launchpadSellFee(bps = 100)
        val net = balance - fee

        assertEquals("$0.20", fee.formatted())
        assertEquals("$19.80", net.formatted())
    }

    @Test
    fun `grossing up a 100 percent fee returns the amount unchanged instead of dividing by zero`() {
        val net = Fiat(20.0)

        assertEquals(net, net.grossingUpLaunchpadSellFee(bps = 10_000))
    }

    @Test
    fun `bps above 100 percent is clamped rather than producing a negative gross-up`() {
        val net = Fiat(20.0)

        // Clamped to 10_000 → guard returns self, never a negative divisor.
        assertEquals(net, net.grossingUpLaunchpadSellFee(bps = 12_000))
    }

    @Test
    fun `zero bps is a no-op`() {
        val net = Fiat(20.0)

        assertEquals(Fiat.Zero.quarks, net.launchpadSellFee(bps = 0).quarks)
        assertEquals(net, net.grossingUpLaunchpadSellFee(bps = 0))
    }
}
