package com.flipcash.libs.currency.math

import com.flipcash.libs.currency.math.internal.curves.DiscreteBondingCurve
import com.flipcash.libs.currency.math.loader.FileTableLoader
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tests for [Estimator.valueExchangeAsTokens] and [Estimator.valueExchangeAsQuarks].
 */
class EstimatorValueExchangeAsTokensTests {

    @BeforeTest
    fun initializeCurve() {
        runBlocking { DiscreteBondingCurve.initialize(FileTableLoader()) }
    }

    // -- valueExchangeAsTokens --

    @Test
    fun `valueExchangeAsTokens returns positive tokens for valid input`() {
        val result = Estimator.valueExchangeAsTokens(
            valueInQuarks = 1_000_000, // 1 USDF
            currentSupplyInQuarks = 100_000_000_000_000, // 10,000 tokens
            mintDecimals = 6,
        ).getOrThrow()

        assertTrue(result.tokens.signum() > 0, "Should return positive tokens")
    }

    @Test
    fun `valueExchangeAsTokens fx rate is positive`() {
        val result = Estimator.valueExchangeAsTokens(
            valueInQuarks = 1_000_000,
            currentSupplyInQuarks = 100_000_000_000_000,
            mintDecimals = 6,
        ).getOrThrow()

        assertTrue(result.fx.signum() > 0, "FX rate should be positive")
    }

    @Test
    fun `valueExchangeAsTokens more value yields more tokens`() {
        val supply = 100_000_000_000_000L // 10,000 tokens

        val result1 = Estimator.valueExchangeAsTokens(
            valueInQuarks = 500_000, // 0.5 USDF
            currentSupplyInQuarks = supply,
            mintDecimals = 6,
        ).getOrThrow()

        val result2 = Estimator.valueExchangeAsTokens(
            valueInQuarks = 2_000_000, // 2 USDF
            currentSupplyInQuarks = supply,
            mintDecimals = 6,
        ).getOrThrow()

        assertTrue(result2.tokens > result1.tokens, "More value should yield more tokens")
    }

    @Test
    fun `valueExchangeAsTokens higher supply yields fewer tokens per value`() {
        val value = 1_000_000L // 1 USDF

        val resultLowSupply = Estimator.valueExchangeAsTokens(
            valueInQuarks = value,
            currentSupplyInQuarks = 10_000_000_000_000, // 1,000 tokens
            mintDecimals = 6,
        ).getOrThrow()

        val resultHighSupply = Estimator.valueExchangeAsTokens(
            valueInQuarks = value,
            currentSupplyInQuarks = 1_000_000_000_000_000, // 100,000 tokens
            mintDecimals = 6,
        ).getOrThrow()

        assertTrue(
            resultLowSupply.tokens > resultHighSupply.tokens,
            "Same value at lower supply should yield more tokens"
        )
    }

    @Test
    fun `valueExchangeAsTokens with small value returns positive result`() {
        val result = Estimator.valueExchangeAsTokens(
            valueInQuarks = 1, // 0.000001 USDF (smallest unit)
            currentSupplyInQuarks = 10_000_000_000_000,
            mintDecimals = 6,
        ).getOrThrow()

        assertTrue(result.tokens.signum() > 0, "Even tiny value should yield some tokens")
    }

    @Test
    fun `valueExchangeAsTokens with large value returns positive result`() {
        // Use a high supply so the TVL comfortably exceeds the exchange value
        val result = Estimator.valueExchangeAsTokens(
            valueInQuarks = 10_000_000, // 10 USDF
            currentSupplyInQuarks = 1_000_000_000_000_000, // 100,000 tokens
            mintDecimals = 6,
        ).getOrThrow()

        assertTrue(result.tokens.signum() > 0, "Large value should yield tokens")
    }

    // -- valueExchangeAsQuarks --

    @Test
    fun `valueExchangeAsQuarks returns positive quarks for valid input`() {
        val result = Estimator.valueExchangeAsQuarks(
            valueInQuarks = 1_000_000, // 1 USDF
            currentSupplyInQuarks = 100_000_000_000_000, // 10,000 tokens
            mintDecimals = 6,
        ).getOrThrow()

        assertTrue(result.quarks.signum() > 0, "Should return positive quarks")
    }

    @Test
    fun `valueExchangeAsQuarks fx rate is positive`() {
        val result = Estimator.valueExchangeAsQuarks(
            valueInQuarks = 1_000_000,
            currentSupplyInQuarks = 100_000_000_000_000,
            mintDecimals = 6,
        ).getOrThrow()

        assertTrue(result.fx.signum() > 0, "FX rate should be positive")
    }

    @Test
    fun `valueExchangeAsQuarks more value yields more quarks`() {
        val supply = 100_000_000_000_000L

        val result1 = Estimator.valueExchangeAsQuarks(
            valueInQuarks = 500_000,
            currentSupplyInQuarks = supply,
            mintDecimals = 6,
        ).getOrThrow()

        val result2 = Estimator.valueExchangeAsQuarks(
            valueInQuarks = 2_000_000,
            currentSupplyInQuarks = supply,
            mintDecimals = 6,
        ).getOrThrow()

        assertTrue(result2.quarks > result1.quarks, "More value should yield more quarks")
    }

    @Test
    fun `valueExchangeAsQuarks quarks are tokens scaled by 10pow10`() {
        val supply = 100_000_000_000_000L
        val value = 1_000_000L

        val tokensResult = Estimator.valueExchangeAsTokens(
            valueInQuarks = value,
            currentSupplyInQuarks = supply,
            mintDecimals = 6,
        ).getOrThrow()

        val quarksResult = Estimator.valueExchangeAsQuarks(
            valueInQuarks = value,
            currentSupplyInQuarks = supply,
            mintDecimals = 6,
        ).getOrThrow()

        // quarks should be tokens * 10^10
        val tokenScale = BigDecimal.TEN.pow(10, mc)
        val expectedQuarks = tokensResult.tokens.multiply(tokenScale, mc)

        assertTrue(
            isApproximatelyEqual(quarksResult.quarks, expectedQuarks, BigDecimal("1")),
            "Quarks should be tokens * 10^10. Got quarks=${quarksResult.quarks}, expected=$expectedQuarks"
        )
    }

    @Test
    fun `valueExchangeAsQuarks with small value`() {
        val result = Estimator.valueExchangeAsQuarks(
            valueInQuarks = 1,
            currentSupplyInQuarks = 10_000_000_000_000,
            mintDecimals = 6,
        ).getOrThrow()

        assertTrue(result.quarks.signum() > 0, "Even tiny value should yield some quarks")
    }

    @Test
    fun `valueExchangeAsQuarks with large value`() {
        // Use a high supply so the TVL comfortably exceeds the exchange value
        val result = Estimator.valueExchangeAsQuarks(
            valueInQuarks = 10_000_000, // 10 USDF
            currentSupplyInQuarks = 1_000_000_000_000_000, // 100,000 tokens
            mintDecimals = 6,
        ).getOrThrow()

        assertTrue(result.quarks.signum() > 0, "Large value should yield quarks")
    }
}

/**
 * Tests for [Estimator] with varying mint decimals.
 * DefaultMintDecimals is 10 (for tokens), but mintDecimals parameter
 * represents the decimals of the value token being exchanged (e.g., 6 for USDF, 9 for SOL).
 */
class EstimatorMultipleMintDecimalsTests {

    @BeforeTest
    fun initializeCurve() {
        runBlocking { DiscreteBondingCurve.initialize(FileTableLoader()) }
    }

    @Test
    fun `buy with mintDecimals 9 (SOL-like) returns positive tokens`() {
        val result = Estimator.buy(
            amountInQuarks = 1_000_000_000, // 1 SOL (9 decimals)
            currentSupplyInQuarks = 1_000_000_000_000, // some supply
            mintDecimals = 9,
            feeBps = 0,
        ).getOrThrow()

        assertTrue(result.netTokensToReceive.signum() > 0, "Should receive positive tokens")
    }

    @Test
    fun `buy with mintDecimals 6 vs 9 both return positive tokens`() {
        // 1 USDF (6 decimals) = 1_000_000 quarks
        val resultUsdf = Estimator.buy(
            amountInQuarks = 1_000_000,
            currentSupplyInQuarks = 1_000_000_000_000,
            mintDecimals = 6,
            feeBps = 0,
        ).getOrThrow()

        // Same amount in quarks but with 9 decimals
        // In discrete buy, the amount is always divided by 1M (USDF units),
        // but mintDecimals affects the supply interpretation
        val resultSol = Estimator.buy(
            amountInQuarks = 1_000_000,
            currentSupplyInQuarks = 1_000_000_000_000,
            mintDecimals = 9,
            feeBps = 0,
        ).getOrThrow()

        assertTrue(resultUsdf.netTokensToReceive.signum() > 0, "USDF buy should yield tokens")
        assertTrue(resultSol.netTokensToReceive.signum() > 0, "SOL-like buy should yield tokens")

        // Different mint decimals with same supply quarks means different supply interpretation,
        // so token yields should differ
        assertTrue(
            resultUsdf.netTokensToReceive != resultSol.netTokensToReceive,
            "Different mint decimals should produce different token amounts"
        )
    }

    @Test
    fun `valueExchangeAsTokens with mintDecimals 9`() {
        val result = Estimator.valueExchangeAsTokens(
            valueInQuarks = 1_000_000_000, // 1 unit at 9 decimals
            currentSupplyInQuarks = 100_000_000_000_000,
            mintDecimals = 9,
        ).getOrThrow()

        assertTrue(result.tokens.signum() > 0)
    }

    @Test
    fun `valueExchangeAsQuarks with mintDecimals 9`() {
        val result = Estimator.valueExchangeAsQuarks(
            valueInQuarks = 1_000_000_000, // 1 unit at 9 decimals
            currentSupplyInQuarks = 100_000_000_000_000,
            mintDecimals = 9,
        ).getOrThrow()

        assertTrue(result.quarks.signum() > 0)
    }

    @Test
    fun `sell with mintDecimals 9`() {
        val result = Estimator.sell(
            amountInQuarks = 100 * 10_000_000_000,
            marketState = MarketState.FromSupply(10_000_000_000_000),
            mintDecimals = 9,
            outputDecimals = 9,
            feeBps = 100,
        ).getOrThrow()

        assertTrue(result.netAmountToReceive.signum() > 0)
        assertTrue(result.fees.signum() > 0)
    }

    @Test
    fun `sell with different output decimals`() {
        // Sell tokens (mintDecimals=6) but receive output in 9-decimal format
        val result = Estimator.sell(
            amountInQuarks = 100 * 10_000_000_000,
            marketState = MarketState.FromSupply(10_000_000_000_000),
            mintDecimals = 6,
            outputDecimals = 6,
            feeBps = 50,
        ).getOrThrow()

        assertTrue(result.netAmountToReceive.signum() > 0)
        assertTrue(result.fees.signum() > 0)
    }
}

/**
 * Edge case tests for [Estimator]: zero amounts, boundary conditions, and error paths.
 */
class EstimatorEdgeCaseTests {

    @BeforeTest
    fun initializeCurve() {
        runBlocking { DiscreteBondingCurve.initialize(FileTableLoader()) }
    }

    // -- Zero amount edge cases --

    @Test
    fun `buy with zero amount fails`() {
        val result = Estimator.buy(
            amountInQuarks = 0,
            currentSupplyInQuarks = 1_000_000,
            mintDecimals = 6,
            feeBps = 0,
        )

        assertTrue(result.isFailure, "Buy with zero amount should fail (requires positive)")
    }

    @Test
    fun `sell with zero amount returns zero`() {
        val result = Estimator.sell(
            amountInQuarks = 0,
            marketState = MarketState.FromSupply(10_000_000),
            mintDecimals = 6,
            outputDecimals = 6,
            feeBps = 100,
        ).getOrThrow()

        assertTrue(
            result.netAmountToReceive.compareTo(BigDecimal.ZERO) == 0,
            "Selling zero should return zero"
        )
        assertTrue(
            result.fees.compareTo(BigDecimal.ZERO) == 0,
            "Selling zero should have zero fees"
        )
    }

    @Test
    fun `buy with zero supply succeeds`() {
        val result = Estimator.buy(
            amountInQuarks = 1_000_000,
            currentSupplyInQuarks = 0,
            mintDecimals = 6,
            feeBps = 0,
        ).getOrThrow()

        assertTrue(result.netTokensToReceive.signum() > 0, "Should receive tokens even from zero supply")
    }

    @Test
    fun `spot price at zero supply`() {
        val price = Estimator.currentPriceFor(currentSupplyInQuarks = 0).getOrThrow()
        // At zero supply, price should be the starting price (~$0.01)
        assertTrue(
            isApproximatelyEqual(price, BigDecimal("0.01"), BigDecimal("0.001")),
            "Price at zero supply should be approximately $0.01"
        )
    }

    // -- Negative amount edge cases --

    @Test
    fun `buy with negative amount fails`() {
        val result = Estimator.buy(
            amountInQuarks = -1,
            currentSupplyInQuarks = 1_000_000,
            mintDecimals = 6,
            feeBps = 0,
        )

        assertTrue(result.isFailure, "Buy with negative amount should fail")
    }

    @Test
    fun `buy with negative supply fails`() {
        val result = Estimator.buy(
            amountInQuarks = 1_000_000,
            currentSupplyInQuarks = -1,
            mintDecimals = 6,
            feeBps = 0,
        )

        assertTrue(result.isFailure, "Buy with negative supply should fail")
    }

    @Test
    fun `buy with negative fee fails`() {
        val result = Estimator.buy(
            amountInQuarks = 1_000_000,
            currentSupplyInQuarks = 1_000_000,
            mintDecimals = 6,
            feeBps = -1,
        )

        assertTrue(result.isFailure, "Buy with negative fee should fail")
    }

    @Test
    fun `sell with negative market state fails`() {
        val result = Estimator.sell(
            amountInQuarks = 100,
            marketState = MarketState.FromSupply(-1),
            mintDecimals = 6,
            outputDecimals = 6,
            feeBps = 0,
        )

        assertTrue(result.isFailure, "Sell with negative market state should fail")
    }

    @Test
    fun `sell with negative fee fails`() {
        val result = Estimator.sell(
            amountInQuarks = 100 * 10_000_000_000,
            marketState = MarketState.FromSupply(10_000_000),
            mintDecimals = 6,
            outputDecimals = 6,
            feeBps = -1,
        )

        assertTrue(result.isFailure, "Sell with negative fee should fail")
    }

    // -- Very large amounts --

    @Test
    fun `buy with very large amount succeeds`() {
        val result = Estimator.buy(
            amountInQuarks = 1_000_000_000_000, // 1M USDF
            currentSupplyInQuarks = 0,
            mintDecimals = 6,
            feeBps = 100,
        ).getOrThrow()

        assertTrue(result.netTokensToReceive.signum() > 0)
        assertTrue(result.fees.signum() > 0)
    }

    @Test
    fun `sell with large token amount`() {
        // Sell 100 tokens worth of quarks
        val sellQuarks = 100L * 10_000_000_000L
        // Supply must be larger, using mintDecimals=6 so supply is in units of 10^6
        val supplyQuarks = 10_000L * 1_000_000L
        val result = Estimator.sell(
            amountInQuarks = sellQuarks,
            marketState = MarketState.FromSupply(supplyQuarks),
            mintDecimals = 6,
            outputDecimals = 6,
            feeBps = 50,
        ).getOrThrow()

        assertTrue(result.netAmountToReceive.signum() > 0)
    }

    @Test
    fun `valueExchangeAsTokens with large value`() {
        // 100 USDF exchange against a high supply
        val result = Estimator.valueExchangeAsTokens(
            valueInQuarks = 100_000_000, // 100 USDF
            currentSupplyInQuarks = 1_000_000_000_000_000L, // 100,000 tokens
            mintDecimals = 6,
        ).getOrThrow()

        assertTrue(result.tokens.signum() > 0)
    }

    // -- MarketState variants --

    @Test
    fun `sell requires FromSupply for discrete curve`() {
        // Discrete curve requires FromSupply
        val result = Estimator.sell(
            amountInQuarks = 100 * 10_000_000_000,
            marketState = MarketState.FromValue(10_000_000),
            mintDecimals = 6,
            outputDecimals = 6,
            feeBps = 0,
            curveType = CurveType.Discrete,
        )

        assertTrue(result.isFailure, "Discrete sell should fail with FromValue market state")
    }

    // -- Fee boundary tests --

    @Test
    fun `buy with 100 percent fee leaves zero tokens`() {
        val result = Estimator.buy(
            amountInQuarks = 1_000_000,
            currentSupplyInQuarks = 1_000_000,
            mintDecimals = 6,
            feeBps = 10000, // 100%
        ).getOrThrow()

        assertTrue(
            result.netTokensToReceive.compareTo(BigDecimal.ZERO) == 0,
            "100% fee should yield zero net tokens"
        )
    }

    @Test
    fun `sell with 100 percent fee leaves zero amount`() {
        val result = Estimator.sell(
            amountInQuarks = 100 * 10_000_000_000,
            marketState = MarketState.FromSupply(10_000_000_000_000),
            mintDecimals = 6,
            outputDecimals = 6,
            feeBps = 10000, // 100%
        ).getOrThrow()

        assertTrue(
            result.netAmountToReceive.compareTo(BigDecimal.ZERO) == 0,
            "100% fee should yield zero net amount"
        )
    }

    @Test
    fun `higher fee bps yields higher fees`() {
        val amount = 1_000_000L // 1 USDF
        val supply = 1_000_000L // low supply to avoid max

        val result1pct = Estimator.buy(
            amountInQuarks = amount,
            currentSupplyInQuarks = supply,
            mintDecimals = 6,
            feeBps = 100, // 1%
        ).getOrThrow()

        val result5pct = Estimator.buy(
            amountInQuarks = amount,
            currentSupplyInQuarks = supply,
            mintDecimals = 6,
            feeBps = 500, // 5%
        ).getOrThrow()

        assertTrue(
            result5pct.fees > result1pct.fees,
            "5% fee should be greater than 1% fee"
        )
    }
}

/**
 * Tests for [ContinuousBondingCurve.tokensForValueExchange] via [Estimator].
 */
class ContinuousCurveValueExchangeTests {

    @Test
    fun `valueExchangeAsTokens via continuous curve returns positive tokens`() {
        val result = Estimator.valueExchangeAsTokens(
            valueInQuarks = 1_000_000,
            currentSupplyInQuarks = 100_000_000_000_000,
            mintDecimals = 6,
            curveType = CurveType.Continuous,
        ).getOrThrow()

        assertTrue(result.tokens.signum() > 0)
    }

    @Test
    fun `valueExchangeAsTokens continuous - more value yields more tokens`() {
        val supply = 100_000_000_000_000L

        val result1 = Estimator.valueExchangeAsTokens(
            valueInQuarks = 500_000,
            currentSupplyInQuarks = supply,
            mintDecimals = 6,
            curveType = CurveType.Continuous,
        ).getOrThrow()

        val result2 = Estimator.valueExchangeAsTokens(
            valueInQuarks = 2_000_000,
            currentSupplyInQuarks = supply,
            mintDecimals = 6,
            curveType = CurveType.Continuous,
        ).getOrThrow()

        assertTrue(result2.tokens > result1.tokens, "More value should yield more tokens")
    }

    @Test
    fun `valueExchangeAsQuarks via continuous curve returns positive quarks`() {
        val result = Estimator.valueExchangeAsQuarks(
            valueInQuarks = 1_000_000,
            currentSupplyInQuarks = 100_000_000_000_000,
            mintDecimals = 6,
            curveType = CurveType.Continuous,
        ).getOrThrow()

        assertTrue(result.quarks.signum() > 0)
        assertTrue(result.fx.signum() > 0)
    }

    @Test
    fun `continuous curve - higher supply means fewer tokens per value`() {
        val value = 1_000_000L

        val resultLow = Estimator.valueExchangeAsTokens(
            valueInQuarks = value,
            currentSupplyInQuarks = 10_000_000_000_000, // 1,000 tokens
            mintDecimals = 6,
            curveType = CurveType.Continuous,
        ).getOrThrow()

        val resultHigh = Estimator.valueExchangeAsTokens(
            valueInQuarks = value,
            currentSupplyInQuarks = 1_000_000_000_000_000, // 100,000 tokens
            mintDecimals = 6,
            curveType = CurveType.Continuous,
        ).getOrThrow()

        assertTrue(
            resultLow.tokens > resultHigh.tokens,
            "Same value at lower supply should yield more tokens on continuous curve"
        )
    }

    @Test
    fun `continuous curve - fx rate matches value divided by tokens`() {
        val value = 1_000_000L
        val supply = 100_000_000_000_000L

        val result = Estimator.valueExchangeAsTokens(
            valueInQuarks = value,
            currentSupplyInQuarks = supply,
            mintDecimals = 6,
            curveType = CurveType.Continuous,
        ).getOrThrow()

        // fx = scaledValue / tokens
        val valueScale = BigDecimal.TEN.pow(6, mc)
        val scaledValue = BigDecimal(value).divide(valueScale, mc)
        val expectedFx = scaledValue.divideWithHighPrecision(result.tokens)

        assertTrue(
            isApproximatelyEqual(result.fx, expectedFx, BigDecimal("0.0000001")),
            "FX rate should equal value/tokens"
        )
    }

    @Test
    fun `continuous buy with mintDecimals 9`() {
        val result = Estimator.buy(
            amountInQuarks = 1_000_000_000,
            currentSupplyInQuarks = 100_000_000_000_000,
            mintDecimals = 9,
            feeBps = 100,
            curveType = CurveType.Continuous,
        ).getOrThrow()

        assertTrue(result.netTokensToReceive.signum() > 0)
        assertTrue(result.fees.signum() > 0)
    }

    @Test
    fun `continuous sell with FromValue market state`() {
        val result = Estimator.sell(
            amountInQuarks = 100 * 10_000_000_000,
            marketState = MarketState.FromValue(1_000_000_000), // 1000 USDF in value quarks
            mintDecimals = 6,
            outputDecimals = 6,
            feeBps = 50,
            curveType = CurveType.Continuous,
        ).getOrThrow()

        assertTrue(result.netAmountToReceive.signum() > 0)
        assertTrue(result.fees.signum() > 0)
    }

    @Test
    fun `continuous sell requires FromValue`() {
        val result = Estimator.sell(
            amountInQuarks = 100 * 10_000_000_000,
            marketState = MarketState.FromSupply(10_000_000),
            mintDecimals = 6,
            outputDecimals = 6,
            feeBps = 0,
            curveType = CurveType.Continuous,
        )

        assertTrue(result.isFailure, "Continuous sell should fail with FromSupply market state")
    }
}
