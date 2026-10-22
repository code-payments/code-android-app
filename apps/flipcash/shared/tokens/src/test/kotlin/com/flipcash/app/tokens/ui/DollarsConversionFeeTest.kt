package com.flipcash.app.tokens.ui

import com.flipcash.app.core.tokens.SwapPurpose
import com.flipcash.app.tokens.entryAffordableAfterFee
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.DOLLARS_FEE_BPS
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.HOUSE_SELL_FEE_BPS
import com.getcode.opencode.model.financial.LaunchpadMetadata
import com.getcode.opencode.model.financial.launchpadSellFee
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class DollarsConversionFeeTest {

    private val otherMint = Mint(ByteArray(32) { 1 }.toList())
    private val key = PublicKey.fromBase58("11111111111111111111111111111111")

    private fun pool(sellFeeBps: Int) = LaunchpadMetadata(
        currencyConfig = key,
        liquidityPool = key,
        seed = key,
        authority = key,
        mintVault = key,
        coreMintVault = key,
        currentCirculatingSupplyQuarks = 1_000_000L,
        sellFeeBps = sellFeeBps,
        price = Fiat(fiat = 0.01),
        marketCap = Fiat(fiat = 10000.0),
    )

    @Test
    fun `converting from Dollars is fee-free`() {
        assertEquals(0, DOLLARS_FEE_BPS)
        assertEquals(0, SwapViewModel.convertFeeBpsFor(Mint.usdf, sourcePool = null))
    }

    @Test
    fun `converting to Dollars still pays the source pool's sell fee`() {
        assertEquals(250, SwapViewModel.convertFeeBpsFor(otherMint, pool(sellFeeBps = 250)))
    }

    @Test
    fun `converting to Dollars from a pool with no declared fee pays the house rate`() {
        assertEquals(
            HOUSE_SELL_FEE_BPS,
            SwapViewModel.convertFeeBpsFor(otherMint, sourcePool = null),
        )
    }

    @Test
    fun `a Dollars conversion prices a zero fee on the whole balance`() {
        val balance = Fiat(20.0, CurrencyCode.USD)
        val fee = balance.launchpadSellFee(SwapViewModel.convertFeeBpsFor(Mint.usdf, null))

        assertEquals(Fiat(0.0, CurrencyCode.USD), fee)
        assertFalse(fee.isPositive)
    }

    @Test
    fun `entering the whole Dollars balance is not trimmed`() {
        val balance = Fiat(20.0, CurrencyCode.USD)

        assertNull(
            entryAffordableAfterFee(
                entered = 20.0,
                balance = balance,
                feeBps = SwapViewModel.convertFeeBpsFor(Mint.usdf, null),
                feeChargedOnTop = true,
            )
        )
    }

    @Test
    fun `charges no buy fee for a Get from Dollars`() {
        val state = SwapViewModel.State(purpose = SwapPurpose.Convert(Mint.usdf, otherMint))
        assertFalse(state.chargesBuyFee)
    }
}
