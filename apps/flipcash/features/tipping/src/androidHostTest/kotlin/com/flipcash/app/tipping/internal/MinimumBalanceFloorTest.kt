package com.flipcash.app.tipping.internal

import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Rate
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The server rejects a group minimum balance below one cent, so the keypad entry is floored there. */
class MinimumBalanceFloorTest {

    private val jpy = Rate(fx = 150.0, currency = CurrencyCode.JPY)

    @Test
    fun `a conversion that rounds to zero cents is rejected`() {
        // 0.5 JPY at 150 JPY/USD is 0.0033 USD.
        assertNull(CreateGroupViewModel.minimumBalanceFor(0.5, jpy))
    }

    @Test
    fun `one yen rounds up to one cent rather than being sent as a fraction`() {
        // 1 JPY is 0.00667 USD; half-up to cents is the minimum unit, as on iOS.
        assertEquals(0.01, CreateGroupViewModel.minimumBalanceFor(1.0, jpy)!!.decimalValue, 1e-9)
    }

    @Test
    fun `zero and negative entries are rejected`() {
        assertNull(CreateGroupViewModel.minimumBalanceFor(0.0, Rate.oneToOne))
        assertNull(CreateGroupViewModel.minimumBalanceFor(-1.0, Rate.oneToOne))
    }

    @Test
    fun `a sub-cent USD entry is rejected`() {
        assertNull(CreateGroupViewModel.minimumBalanceFor(0.004, Rate.oneToOne))
    }

    @Test
    fun `a fractional-cent conversion rounds to cents`() {
        // 2 JPY at 150 JPY/USD is 0.01333 USD.
        val one = CreateGroupViewModel.minimumBalanceFor(2.0, jpy)
        assertNotNull(one)
        assertEquals(CurrencyCode.USD, one.currencyCode)
        assertEquals(0.01, one.decimalValue, 1e-9)

        // 100 JPY is 0.6667 USD.
        assertEquals(0.67, CreateGroupViewModel.minimumBalanceFor(100.0, jpy)!!.decimalValue, 1e-9)
    }

    @Test
    fun `exactly one cent is accepted`() {
        assertEquals(0.01, CreateGroupViewModel.minimumBalanceFor(0.01, Rate.oneToOne)!!.decimalValue, 1e-9)
    }
}
