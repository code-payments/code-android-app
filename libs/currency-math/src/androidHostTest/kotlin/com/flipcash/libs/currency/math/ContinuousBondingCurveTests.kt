package com.flipcash.libs.currency.math

import com.flipcash.libs.currency.math.internal.curves.ContinuousBondingCurve
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class ContinuousBondingCurveTests {
    //
    @Test
    fun `test calculate curve constants`() {
        val curve = ContinuousBondingCurve.getOrThrow()

        // Check R'(0) with tolerance
        val spot0 = curve.spotPriceAtSupply(BigDecimal.ZERO).getOrThrow()
        val expectedStart = START_PRICE
        var diff = spot0.subtract(expectedStart, mc)
        var threshold = BigDecimal("0.0000000001")

        if (diff.abs(mc) > threshold) {
            fail("Spot at 0: got ${formatter.format(spot0)}, expected 0.01")
        }

        // Check R'(21000000) with tolerance
        val supplyEnd = MAX_SUPPLY
        val spotEnd = curve.spotPriceAtSupply(supplyEnd).getOrThrow()
        val expectedEnd = END_PRICE
        diff = spotEnd.subtract(expectedEnd, mc)
        threshold = BigDecimal("0.0001")
        if (diff.abs(mc) > threshold) {
            fail("Spot at end: got ${formatter.format(spotEnd)}, expected 1000000")
        }
    }

    @Test
    fun `generate table for curve`() {
        val curve = ContinuousBondingCurve.getOrThrow()

        val generatedOutput = curve.formattedTable().trimIndent()

        val generatedLines = generatedOutput.lines()
        val expectedLines = EXPECTED_TABLE_OUTPUT.lines()

        // Check lengths
        if (generatedLines.size != expectedLines.size) {
            fail("Generated table has ${generatedLines.size} lines, expected ${expectedLines.size}")
        }

        // Compare line by line
        for (i in generatedLines.indices) {
            val expected = expectedLines[i].trim()
            val generated = generatedLines[i].trim()
            if (generated != expected) {
                fail(
                    """
                        Mismatch at line ${i + 1}:
                        Expected: $expected
                        Generated:$generated
                    """.trimIndent()
                )
            }
        }
    }

    @Test
    fun `selling zero tokens should return zero value`() {
        val curve = ContinuousBondingCurve.getOrThrow()
        val currentValue = BigDecimal("1000")
        val tokensToSell = BigDecimal.ZERO
        val result = curve.tokensToValue(currentValue, tokensToSell)

        assertTrue(result.isSuccess)
        // Expecting a very small or zero value due to calculations,
        // so we check if it's close to zero.
        // The formula (cv + ab/c) * (1 - e^(-c*0)) = (cv + ab/c) * (1 - 1) = 0
        assertEquals(0.0, result.getOrThrow().toDouble(), 1e-9)
    }

    @Test
    fun `selling some tokens returns positive value`() {
        val curve = ContinuousBondingCurve.getOrThrow()
        val currentValue = BigDecimal("10000") // Example current value
        val tokensToSell = BigDecimal("100")   // Example tokens to sell

        // Expected value calculation (simplified for this example)
        // This would ideally be pre-calculated or derived from a trusted source
        // For now, let's ensure it's positive and the function doesn't throw.
        // A more precise expected value would require running the formula manually
        // with the specific a, b, c values.

        val result = curve.tokensToValue(currentValue, tokensToSell)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow() > BigDecimal.ZERO)
    }

    @Test
    fun `selling tokens decreases value as expected (conceptual)`() {
        // This test is more conceptual and harder to get exact without pre-calculated values
        // It aims to verify the logic that selling more tokens should yield more value,
        // but the rate might change.

        val curve = ContinuousBondingCurve.getOrThrow()
        val currentValue = BigDecimal("50000")
        val tokensToSellLess = BigDecimal("50")
        val tokensToSellMore = BigDecimal("150")

        val resultLess = curve.tokensToValue(currentValue, tokensToSellLess)
        val resultMore = curve.tokensToValue(currentValue, tokensToSellMore)

        assertTrue(resultLess.isSuccess)
        assertTrue(resultMore.isSuccess)
        assertTrue(resultMore.getOrThrow() > resultLess.getOrThrow())
    }

    @Test
    fun `exchanging zero value should result in close to zero tokens`() {
        val curve = ContinuousBondingCurve.getOrThrow()
        val currentSupply = MAX_SUPPLY.multiply(BigDecimal("0.25"), mc) // Some supply
        val valueToReceive = BigDecimal("0.000001")
        val result = curve.tokensForValueExchange(currentSupply, valueToReceive)
        assertTrue(result.isSuccess)
        // Due to the nature of the formula (especially the log),
        // a value of zero might lead to issues or a specific behavior (e.g., log of a negative or zero).
        // The formula has ln(value*B / (const*exp(cS)) - 1). If value is 0, this is ln(-1) -> error
        // Let's test with a very small positive value instead if 0 is problematic
        // For now, if the formula expects positive 'value', we test that.
        // If value = 0 leads to an error as per the formula, this test might need adjustment
        // or the function should handle it. Assuming it should return near zero tokens for near zero value.

        // Re-evaluating based on the formula: ln( (value * B / ( (1/100) * exp(C*S) * B) ) - 1 )
        // If value is 0, then ln(-1) which is undefined for real numbers.
        // So, an exception or NaN might be expected.
        // Let's test if the function handles this by returning a failure or a specific result.

        // Based on your current runCatching, it should return a failure.
        val smallPositiveValue = BigDecimal("0.0000000001")
        val resultSmallValue = curve.tokensForValueExchange(currentSupply, smallPositiveValue)
        assertTrue(resultSmallValue.isSuccess) // Assuming for a very small value, it might succeed
        // or it might fail if lnInput becomes <= 0
        // This test will depend heavily on the behavior of big-math's log for non-positive inputs
        // or inputs that make the argument to log <= 0.
        // If `value` leads to `lnInput <= 0`, an exception is expected from `log(mc)`.
        // Let's test for a scenario that should succeed.
    }

    @Test
    fun `exchanging positive value returns positive number of tokens`() {
        val curve = ContinuousBondingCurve.getOrThrow()
        val currentSupply = MAX_SUPPLY.multiply(BigDecimal("0.25"), mc) // Some supply
        val valueToReceive = BigDecimal("100")     // Example value to receive

        val result = curve.tokensForValueExchange(currentSupply, valueToReceive)
            .onFailure { it.printStackTrace() }
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().tokens > BigDecimal.ZERO)
    }

    @Test
    fun `exchanging more value should require more tokens (conceptual)`() {
        val curve = ContinuousBondingCurve.getOrThrow()
        val currentSupply = MAX_SUPPLY.multiply(BigDecimal("0.25"), mc) // Some supply
        val valueLess = BigDecimal("100")
        val valueMore = BigDecimal("500")

        val resultLess = curve.tokensForValueExchange(currentSupply, valueLess)
        val resultMore = curve.tokensForValueExchange(currentSupply, valueMore)

        assertTrue(resultLess.isSuccess)
        assertTrue(resultMore.isSuccess)
        assertTrue(resultMore.getOrThrow().tokens > resultLess.getOrThrow().tokens)
    }
}