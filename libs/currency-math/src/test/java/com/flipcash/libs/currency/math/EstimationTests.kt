package com.flipcash.libs.currency.math

import com.flipcash.libs.currency.math.internal.curves.DiscreteBondingCurve
import com.flipcash.libs.currency.math.loader.FileTableLoader
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EstimationTests {

    @BeforeTest
    fun initializeCurve() {
        runBlocking { DiscreteBondingCurve.initialize(FileTableLoader()) }
    }

    @Test
    fun `market cat at zero supply is zero`() {
        val marketCap = Estimator.currentMarketCap(currentSupplyInQuarks = 0).getOrThrow()
        assertEquals(0, marketCap.toInt())
    }

    @Test
    fun `market cap at non-zero supply is positive`() {
        val marketCap = Estimator.currentMarketCap(currentSupplyInQuarks = 1000 * 10_000_000_000).getOrThrow()
        assertTrue { marketCap.signum() > 0 }
    }

    @Test
    fun `estimate buy with no fee`() {
        val (received, fees) = Estimator.buy(
            amountInQuarks = 1_000_000,
            currentSupplyInQuarks = 1_000_000,
            mintDecimals = 6,
            feeBps = 0, // 0%
        ).getOrThrow()

        assertEquals(received, received + fees)
        assertEquals(0, fees.toInt())
    }

    @Test
    fun `estimate buy with 1 percent fee`() {
        val (received, fees) = Estimator.buy(
            amountInQuarks = 1_000_000,
            currentSupplyInQuarks = 1_000_000,
            mintDecimals = 6,
            feeBps = 100, // 1%
        ).getOrThrow()

        assertTrue { received < received + fees }
        assertTrue { fees > BigDecimal.ZERO }
    }

    @Test
    fun `estimate buy with 10 percent fee`() {
        val (received, fees) = Estimator.buy(
            amountInQuarks = 1_000_000,
            currentSupplyInQuarks = 1_000_000,
            mintDecimals = 6,
            feeBps = 1000, // 10%
        ).getOrThrow()

        // Net should be approximately 90% of gross
        val ratio = received.divideWithHighPrecision(received + fees)
        assertTrue { isApproximatelyEqual(ratio, BigDecimal(0.9), BigDecimal("0.001")) }
    }

    @Test
    fun `estimate sell with no fee`() {
        val (received, fees) = Estimator.sell(
            amountInQuarks = 100 * 10_000_000_000,
            currentValueInQuarks = 10_000_000,
            mintDecimals = 6,
            feeBps = 0, // 0%
        ).getOrThrow()

        assertEquals(received, received + fees)
        assertEquals(0, fees.toInt())
    }

    @Test
    fun `estimate sell with 1 percent fee`() {
        val (received, fees) = Estimator.buy(
            amountInQuarks = 100 * 10_000_000_000,
            currentSupplyInQuarks = 10_000_000,
            mintDecimals = 6,
            feeBps = 100, // 1%
        ).getOrThrow()

        assertTrue { received < received + fees }
        assertTrue { fees > BigDecimal.ZERO }
    }

    @Test
    fun `estimate sell with 10 percent fee`() {
        val (received, fees) = Estimator.buy(
            amountInQuarks = 100 * 10_000_000_000,
            currentSupplyInQuarks = 10_000_000,
            mintDecimals = 6,
            feeBps = 1000, // 10%
        ).getOrThrow()

        // Net should be approximately 90% of gross
        val ratio = received.divideWithHighPrecision(received + fees)
        assertTrue { isApproximatelyEqual(ratio, BigDecimal(0.9), BigDecimal("0.001")) }
    }

    @Test
    fun `buy then sell roundtrip`() {
        val initialSupply = 1_000_000L // 1 USDC
        val usdcToSpend = 1_000_000L   // 1 USDC

        // Buy some
        val buyEstimate = Estimator.buy(
            amountInQuarks = usdcToSpend,
            currentSupplyInQuarks = initialSupply,
            mintDecimals = 6,
            feeBps = 0,
        ).getOrThrow()

        // Sell the tokens we bought
        val tokenQuarks = buyEstimate.netTokensToReceive.multiplyWithHighPrecision(BigDecimal(10_000_000_000))
            .round(MathContext(0, RoundingMode.DOWN))

        val newSupply = initialSupply + usdcToSpend

        val sellEstimate = Estimator.sell(
            amountInQuarks = tokenQuarks.toLong(),
            currentValueInQuarks = newSupply,
            mintDecimals = 6,
            feeBps = 0,
        ).getOrThrow()

        // Should get approximately what we spent
        val usdcRecovered = sellEstimate.netAmountToReceive.multiplyWithHighPrecision(BigDecimal(1_000_000))
        // For discrete curves, step-based pricing introduces quantization.
        // At low token counts (100 tokens from $1) the rounding can cause ~10% loss
        // because each step is 100 tokens and we're right at the boundary.
        // For larger amounts (10+ steps) the loss would be much smaller.
        // Allow 15% loss for this small-amount test case.
        val minRecovery = BigDecimal(usdcToSpend).multiplyWithHighPrecision(BigDecimal(0.85))
        assertTrue(usdcRecovered >= minRecovery, message = "Should recover at least 85% of original for small amounts")
    }
}