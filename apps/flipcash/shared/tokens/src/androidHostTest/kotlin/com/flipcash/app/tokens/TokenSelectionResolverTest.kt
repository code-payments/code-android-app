package com.flipcash.app.tokens

import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.solana.keys.Mint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TokenSelectionResolverTest {

    private val mintA = Mint("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAaaaaaaaaaaa")
    private val mintB = Mint("BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBbbbbbbbbbbb")
    private val mintC = Mint("CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCccccccccccc")

    private val usdRate = Rate(fx = 1.0, currency = CurrencyCode.USD)

    // region Empty balances

    @Test
    fun `returns null when balances are empty`() {
        val result = resolveTokenSelection(
            balances = emptyMap(),
            currentSelection = mintA,
            rate = usdRate,
        )
        assertNull(result)
    }

    // endregion

    // region Current selection retained

    @Test
    fun `keeps current selection when balance is above threshold`() {
        val result = resolveTokenSelection(
            balances = mapOf(
                mintA to Fiat(5.00, CurrencyCode.USD),
                mintB to Fiat(10.00, CurrencyCode.USD),
            ),
            currentSelection = mintA,
            rate = usdRate,
        )
        assertEquals(mintA, result)
    }

    @Test
    fun `keeps current selection at exactly the threshold`() {
        val result = resolveTokenSelection(
            balances = mapOf(
                mintA to Fiat(0.01, CurrencyCode.USD),
            ),
            currentSelection = mintA,
            rate = usdRate,
        )
        assertEquals(mintA, result)
    }

    // endregion

    // region Zero balance triggers re-selection

    @Test
    fun `selects next highest when current balance is zero`() {
        val result = resolveTokenSelection(
            balances = mapOf(
                mintA to Fiat(0.0, CurrencyCode.USD),
                mintB to Fiat(10.00, CurrencyCode.USD),
            ),
            currentSelection = mintA,
            rate = usdRate,
        )
        assertEquals(mintB, result)
    }

    @Test
    fun `selects next highest when current balance drops below threshold`() {
        // 0.004 USD rounds to 0.00 with HALF_UP — below 0.01 threshold
        val result = resolveTokenSelection(
            balances = mapOf(
                mintA to Fiat(0.004, CurrencyCode.USD),
                mintB to Fiat(3.00, CurrencyCode.USD),
            ),
            currentSelection = mintA,
            rate = usdRate,
        )
        assertEquals(mintB, result)
    }

    @Test
    fun `returns null when all balances are zero`() {
        val result = resolveTokenSelection(
            balances = mapOf(
                mintA to Fiat(0.0, CurrencyCode.USD),
                mintB to Fiat(0.0, CurrencyCode.USD),
            ),
            currentSelection = mintA,
            rate = usdRate,
        )
        assertNull(result)
    }

    @Test
    fun `returns null when all balances are below threshold`() {
        // Both round to 0.00 with HALF_UP
        val result = resolveTokenSelection(
            balances = mapOf(
                mintA to Fiat(0.001, CurrencyCode.USD),
                mintB to Fiat(0.004, CurrencyCode.USD),
            ),
            currentSelection = mintA,
            rate = usdRate,
        )
        assertNull(result)
    }

    // endregion

    // region Fallback picks highest balance

    @Test
    fun `selects highest balance token when no current selection`() {
        val result = resolveTokenSelection(
            balances = mapOf(
                mintA to Fiat(5.00, CurrencyCode.USD),
                mintB to Fiat(20.00, CurrencyCode.USD),
                mintC to Fiat(10.00, CurrencyCode.USD),
            ),
            currentSelection = null,
            rate = usdRate,
        )
        assertEquals(mintB, result)
    }

    @Test
    fun `selects highest balance when current selection mint is missing from balances`() {
        val result = resolveTokenSelection(
            balances = mapOf(
                mintB to Fiat(7.00, CurrencyCode.USD),
                mintC to Fiat(3.00, CurrencyCode.USD),
            ),
            currentSelection = mintA,
            rate = usdRate,
        )
        assertEquals(mintB, result)
    }

    // endregion

    // region USDF selection

    @Test
    fun `selects USDF as fallback when it has highest balance`() {
        val result = resolveTokenSelection(
            balances = mapOf(
                mintA to Fiat(0.0, CurrencyCode.USD),
                Mint.usdf to Fiat(100.00, CurrencyCode.USD),
            ),
            currentSelection = mintA,
            rate = usdRate,
        )
        assertEquals(Mint.usdf, result)
    }

    @Test
    fun `selects USDF over lower balance tokens`() {
        val result = resolveTokenSelection(
            balances = mapOf(
                mintA to Fiat(0.0, CurrencyCode.USD),
                Mint.usdf to Fiat(100.00, CurrencyCode.USD),
                mintB to Fiat(5.00, CurrencyCode.USD),
            ),
            currentSelection = mintA,
            rate = usdRate,
        )
        assertEquals(Mint.usdf, result)
    }

    @Test
    fun `retains USDF as current selection if it meets threshold`() {
        val result = resolveTokenSelection(
            balances = mapOf(
                Mint.usdf to Fiat(50.00, CurrencyCode.USD),
                mintA to Fiat(10.00, CurrencyCode.USD),
            ),
            currentSelection = Mint.usdf,
            rate = usdRate,
        )
        assertEquals(Mint.usdf, result)
    }

    // endregion

    // region Exchange rate sensitivity

    @Test
    fun `balance below threshold in USD but valid with high fx rate`() {
        // 0.004 USD rounds to $0.00 at 1:1, but 0.004 × 200 = 0.80 ARS — above 0.01
        val arsRate = Rate(fx = 200.0, currency = CurrencyCode.ARS)
        val result = resolveTokenSelection(
            balances = mapOf(
                mintA to Fiat(0.004, CurrencyCode.USD),
            ),
            currentSelection = mintA,
            rate = arsRate,
        )
        assertEquals(mintA, result)
    }

    @Test
    fun `balance above zero in USD but below threshold in native currency`() {
        // 0.004 USD × 1.0 EUR/USD = 0.004 EUR — rounds to 0.00, below 0.01
        val eurRate = Rate(fx = 1.0, currency = CurrencyCode.EUR)
        val result = resolveTokenSelection(
            balances = mapOf(
                mintA to Fiat(0.004, CurrencyCode.USD),
                mintB to Fiat(1.00, CurrencyCode.USD),
            ),
            currentSelection = mintA,
            rate = eurRate,
        )
        assertEquals(mintB, result)
    }

    @Test
    fun `tiny USD balance rounds to zero even with moderate fx rate`() {
        // 0.00001 USD × 150 JPY/USD = 0.0015 JPY — below 0.01 JPY threshold
        val jpyRate = Rate(fx = 150.0, currency = CurrencyCode.JPY)
        val result = resolveTokenSelection(
            balances = mapOf(
                mintA to Fiat(0.00001, CurrencyCode.USD),
            ),
            currentSelection = mintA,
            rate = jpyRate,
        )
        assertNull(result)
    }

    @Test
    fun `selection changes when rate makes current balance fall below threshold`() {
        // At 1:1 rate, 0.02 USD = 0.02 in native → above threshold
        // At 0.2 rate, 0.02 USD = 0.004 in native → rounds to 0.00, below threshold
        val lowRate = Rate(fx = 0.2, currency = CurrencyCode.GBP)
        val result = resolveTokenSelection(
            balances = mapOf(
                mintA to Fiat(0.02, CurrencyCode.USD),
                mintB to Fiat(1.00, CurrencyCode.USD),
            ),
            currentSelection = mintA,
            rate = lowRate,
        )
        assertEquals(mintB, result)
    }

    @Test
    fun `high fx rate rescues very small balances`() {
        // 0.0001 USD × 1200 ARS/USD = 0.12 ARS — above 0.01 ARS threshold
        val arsRate = Rate(fx = 1200.0, currency = CurrencyCode.ARS)
        val result = resolveTokenSelection(
            balances = mapOf(
                mintA to Fiat(0.0001, CurrencyCode.USD),
            ),
            currentSelection = mintA,
            rate = arsRate,
        )
        assertEquals(mintA, result)
    }

    // endregion
}
