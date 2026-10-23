package com.flipcash.app.tokens

import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.grossingUpLaunchpadSellFee
import com.getcode.opencode.model.financial.launchpadSellFee
import com.getcode.opencode.model.financial.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeeAffordableEntryTest {

    private fun usd(value: Double) = Fiat(value, CurrencyCode.USD)

    // --- Entries that already fit ---

    @Test
    fun `an entry with room for its fee is left exactly as typed`() {
        assertNull(
            entryAffordableAfterFee(
                entered = 5.0,
                balance = usd(10.0),
                feeBps = 100,
                feeChargedOnTop = true,
            )
        )
    }

    @Test
    fun `a free conversion never corrects the entry`() {
        assertNull(
            entryAffordableAfterFee(
                entered = 10.0,
                balance = usd(10.0),
                feeBps = 0,
                feeChargedOnTop = true,
            )
        )
    }

    // --- Entering the maximum ---

    @Test
    fun `the whole balance drops to what a fee charged on top leaves`() {
        val corrected = entryAffordableAfterFee(
            entered = 10.0,
            balance = usd(10.0),
            feeBps = 100,
            feeChargedOnTop = true,
        )

        assertEquals("$9.90", corrected?.formatted())
        assertTrue(corrected!! + corrected.launchpadSellFee(100) <= usd(10.0))
    }

    @Test
    fun `the whole balance drops to what a grossed-up fee leaves`() {
        val corrected = entryAffordableAfterFee(
            entered = 10.0,
            balance = usd(10.0),
            feeBps = 100,
            feeChargedOnTop = false,
        )

        assertEquals("$9.90", corrected?.formatted())
        assertTrue(corrected!!.grossingUpLaunchpadSellFee(100) <= usd(10.0))
    }

    @Test
    fun `the correction is floored so the fee still fits when rounding would not`() {
        // $10.11 / 1.01 = $10.0099, which rounds up to $10.01 — and $10.01 plus its own 1% fee is
        // $10.1101, back over the balance. Flooring to $10.00 keeps the debit inside it.
        val corrected = entryAffordableAfterFee(
            entered = 10.11,
            balance = usd(10.11),
            feeBps = 100,
            feeChargedOnTop = true,
        )

        assertEquals("$10.00", corrected?.formatted())
        assertTrue(corrected!! + corrected.launchpadSellFee(100) <= usd(10.11))
    }

    @Test
    fun `re-entering the corrected amount does not correct it again`() {
        val corrected = entryAffordableAfterFee(
            entered = 10.0,
            balance = usd(10.0),
            feeBps = 100,
            feeChargedOnTop = true,
        )

        assertNull(
            entryAffordableAfterFee(
                entered = corrected!!.decimalValue,
                balance = usd(10.0),
                feeBps = 100,
                feeChargedOnTop = true,
            )
        )
    }

    @Test
    fun `an entry beyond the balance is corrected down to the maximum too`() {
        val corrected = entryAffordableAfterFee(
            entered = 50.0,
            balance = usd(10.0),
            feeBps = 100,
            feeChargedOnTop = true,
        )

        assertEquals("$9.90", corrected?.formatted())
    }

    @Test
    fun `the correction is denominated in the balance's currency`() {
        val corrected = entryAffordableAfterFee(
            entered = 1000.0,
            balance = Fiat(1000.0, CurrencyCode.JPY),
            feeBps = 100,
            feeChargedOnTop = true,
        )

        // ¥ has no fractional unit, so the correction floors to whole yen.
        assertEquals(CurrencyCode.JPY, corrected?.currencyCode)
        assertEquals(Fiat(990.0, CurrencyCode.JPY).quarks, corrected?.quarks)
    }
}
