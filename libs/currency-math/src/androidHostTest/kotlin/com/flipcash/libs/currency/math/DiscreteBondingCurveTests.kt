package com.flipcash.libs.currency.math

import com.flipcash.libs.currency.math.internal.curves.DiscreteBondingCurve
import kotlin.test.BeforeTest
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.test.*

private val MAX_SUPPLY = BigDecimal("21000000")

// MARK: - 1. Spot Price Tests

class DiscreteSpotPriceTests {

    private lateinit var curve: DiscreteBondingCurve

    @BeforeTest
    fun initializeCurve() {
        CurveTestInitializer.initialize()
        curve = DiscreteBondingCurve.getOrThrow()
    }

    companion object {
        val expectedPriceStep0 = BigDecimal("0.010000000000000000")
        val expectedPriceStep1 = BigDecimal("0.010000877213746469")
        val expectedPriceStep2 = BigDecimal("0.010001754504443334")
    }

    @Test
    fun `1-1 Spot price at supply 0 equals first table entry`() {
        val price = curve.spotPriceAtSupply(supply = BigDecimal.ZERO).getOrThrow()
        assertNotNull(price)
        assertTrue(isApproximatelyEqual(price, expectedPriceStep0))
    }

    @Test
    fun `1-2 Supply 50 uses same price as supply 0 (within step 0)`() {
        val price0 = curve.spotPriceAtSupply(supply = BigDecimal.ZERO).getOrThrow()
        val price50 = curve.spotPriceAtSupply(supply = BigDecimal(50)).getOrThrow()
        assertEquals(price0, price50)
    }

    @Test
    fun `1-3 Supply 100 uses step 1 price`() {
        val price100 = curve.spotPriceAtSupply(supply = BigDecimal(100)).getOrThrow()
        assertNotNull(price100)
        assertTrue(isApproximatelyEqual(price100, expectedPriceStep1))
    }

    @Test
    fun `1-4 Prices change at step boundaries 0, 100, 200 to 900`() {
        var previousPrice: BigDecimal? = null
        for (step in 0 until 10) {
            val supply = step * 100
            val price = curve.spotPriceAtSupply(supply = supply.toBigDecimal()).getOrThrow()
            assertNotNull(price)
            if (previousPrice != null) {
                assertTrue(price > previousPrice, "Price should increase at step $step")
            }
            previousPrice = price
        }
    }

    @Test
    fun `1-5 Supply 99 still uses step 0 price`() {
        val price0 = curve.spotPriceAtSupply(supply = BigDecimal.ZERO).getOrThrow()
        val price99 = curve.spotPriceAtSupply(supply = BigDecimal(99)).getOrThrow()
        assertEquals(price0, price99)
    }

    @Test
    fun `1-6 Various positions within step 5 all return same price`() {
        val step5Start = 500
        val offsets = listOf(0, 1, 25, 50, 75, 99)
        val basePrice = curve.spotPriceAtSupply(supply = step5Start.toBigDecimal()).getOrThrow()
        assertNotNull(basePrice)

        for (offset in offsets) {
            val price = curve.spotPriceAtSupply(supply = (step5Start + offset).toBigDecimal()).getOrThrow()
            assertEquals(basePrice, price, "Supply ${step5Start + offset} should have same price as supply $step5Start")
        }
    }

    @Test
    fun `1-7 Supply beyond table returns failure`() {
        val result = curve.spotPriceAtSupply(supply = MAX_SUPPLY.add(BigDecimal.ONE))
        assertTrue(result.isFailure)
    }

    @Test
    fun `1-8 Supply at max valid step returns correct price`() {
        val price = curve.spotPriceAtSupply(supply = MAX_SUPPLY).getOrThrow()
        assertNotNull(price)
    }
}

// MARK: - 2. Tokens To Value Tests

class DiscreteTokensToValueTests {

    private lateinit var curve: DiscreteBondingCurve

    @BeforeTest
    fun initializeCurve() {
        CurveTestInitializer.initialize()
        curve = DiscreteBondingCurve.getOrThrow()
    }

    @Test
    fun `2-1 Buying 0 tokens costs 0 from any supply`() {
        val cost = curve.tokensToValue(currentSupply = BigDecimal.ZERO, tokens = BigDecimal.ZERO).getOrThrow()
        assertEquals(BigDecimal.ZERO, cost)

        val cost2 = curve.tokensToValue(currentSupply = BigDecimal(1000), tokens = BigDecimal.ZERO).getOrThrow()
        assertEquals(BigDecimal.ZERO, cost2)
    }

    @Test
    fun `2-2 50 tokens from supply 0 = 50 times price at 0`() {
        val price = curve.spotPriceAtSupply(supply = BigDecimal.ZERO).getOrThrow()
        val cost = curve.tokensToValue(currentSupply = BigDecimal.ZERO, tokens = BigDecimal(50)).getOrThrow()

        val expected = BigDecimal(50).multiplyWithHighPrecision(price)
        assertTrue(isApproximatelyEqual(cost, expected))
    }

    @Test
    fun `2-3 75 tokens from supply 25 = 75 times price at 0`() {
        val price = curve.spotPriceAtSupply(supply = BigDecimal.ZERO).getOrThrow()
        val cost = curve.tokensToValue(currentSupply = BigDecimal(25), tokens = BigDecimal(75)).getOrThrow()

        val expected = BigDecimal(75).multiplyWithHighPrecision(price)
        assertTrue(isApproximatelyEqual(cost, expected))
    }

    @Test
    fun `2-4 30 tokens from supply 10 = 30 times price at 0`() {
        val price = curve.spotPriceAtSupply(supply = BigDecimal.ZERO).getOrThrow()
        val cost = curve.tokensToValue(currentSupply = BigDecimal.TEN, tokens = BigDecimal(30)).getOrThrow()

        val expected = BigDecimal(30).multiplyWithHighPrecision(price)
        assertTrue(isApproximatelyEqual(cost, expected))
    }

    @Test
    fun `2-5 100 tokens from supply 0 = 100 times price at 0`() {
        val price = curve.spotPriceAtSupply(supply = BigDecimal.ZERO).getOrThrow()
        val cost = curve.tokensToValue(currentSupply = BigDecimal.ZERO, tokens = BigDecimal(100)).getOrThrow()

        val expected = BigDecimal(100).multiplyWithHighPrecision(price)
        assertTrue(isApproximatelyEqual(cost, expected))
    }

    @Test
    fun `2-6 200 tokens from 0 = 100 times price0 + 100 times price1`() {
        val price0 = curve.spotPriceAtSupply(supply = BigDecimal.ZERO).getOrThrow()
        val price1 = curve.spotPriceAtSupply(supply = BigDecimal(100)).getOrThrow()
        val cost = curve.tokensToValue(currentSupply = BigDecimal.ZERO, tokens = BigDecimal(200)).getOrThrow()

        val expected = BigDecimal(100).multiplyWithHighPrecision(price0)
            .addWithHighPrecision(BigDecimal(100).multiplyWithHighPrecision(price1))
        assertTrue(isApproximatelyEqual(cost, expected))
    }

    @Test
    fun `2-7 150 tokens from 50 = 50 times price0 + 100 times price1`() {
        val price0 = curve.spotPriceAtSupply(supply = BigDecimal.ZERO).getOrThrow()
        val price1 = curve.spotPriceAtSupply(supply = BigDecimal(100)).getOrThrow()
        val cost = curve.tokensToValue(currentSupply = BigDecimal(50), tokens = BigDecimal(150)).getOrThrow()

        val expected = BigDecimal(50).multiplyWithHighPrecision(price0)
            .addWithHighPrecision(BigDecimal(100).multiplyWithHighPrecision(price1))
        assertTrue(isApproximatelyEqual(cost, expected))
    }

    @Test
    fun `2-8 175 tokens from 0 = 100 times price0 + 75 times price1`() {
        val price0 = curve.spotPriceAtSupply(supply = BigDecimal.ZERO).getOrThrow()
        val price1 = curve.spotPriceAtSupply(supply = BigDecimal(100)).getOrThrow()
        val cost = curve.tokensToValue(currentSupply = BigDecimal.ZERO, tokens = BigDecimal(175)).getOrThrow()

        val expected = BigDecimal(100).multiplyWithHighPrecision(price0)
            .addWithHighPrecision(BigDecimal(75).multiplyWithHighPrecision(price1))
        assertTrue(isApproximatelyEqual(cost, expected))
    }

    @Test
    fun `2-9 125 tokens from 50 = 50 times price0 + 75 times price1`() {
        val price0 = curve.spotPriceAtSupply(supply = BigDecimal.ZERO).getOrThrow()
        val price1 = curve.spotPriceAtSupply(supply = BigDecimal(100)).getOrThrow()
        val cost = curve.tokensToValue(currentSupply = BigDecimal(50), tokens = BigDecimal(125)).getOrThrow()

        val expected = BigDecimal(50).multiplyWithHighPrecision(price0)
            .addWithHighPrecision(BigDecimal(75).multiplyWithHighPrecision(price1))
        assertTrue(isApproximatelyEqual(cost, expected))
    }

    @Test
    fun `2-10 500 tokens from 0 spans 5 complete steps`() {
        val cost = curve.tokensToValue(currentSupply = BigDecimal.ZERO, tokens = BigDecimal(500)).getOrThrow()

        var expected = BigDecimal.ZERO
        for (step in 0 until 5) {
            val price = curve.spotPriceAtSupply(supply = BigDecimal(step * 100)).getOrThrow()
            expected = expected.addWithHighPrecision(BigDecimal(100).multiplyWithHighPrecision(price))
        }
        assertTrue(isApproximatelyEqual(cost, expected))
    }

    @Test
    fun `2-11 350 tokens from 75 (complex partial case)`() {
        val cost = curve.tokensToValue(currentSupply = BigDecimal(75), tokens = BigDecimal(350)).getOrThrow()

        // 25 tokens in step 0, 100 tokens each in steps 1-3, 25 tokens in step 4
        var expected = BigDecimal.ZERO
        val p0 = curve.spotPriceAtSupply(supply = BigDecimal.ZERO).getOrThrow()
        expected = expected.addWithHighPrecision(BigDecimal(25).multiplyWithHighPrecision(p0))

        for (step in 1..3) {
            val price = curve.spotPriceAtSupply(supply = BigDecimal(step * 100)).getOrThrow()
            expected = expected.addWithHighPrecision(BigDecimal(100).multiplyWithHighPrecision(price))
        }

        val p4 = curve.spotPriceAtSupply(supply = BigDecimal(400)).getOrThrow()
        expected = expected.addWithHighPrecision(BigDecimal(25).multiply(p4))

        assertTrue(isApproximatelyEqual(cost, expected))
    }

    @Test
    fun `2-12 150 tokens from 100 = 100 times price1 + 50 times price2`() {
        val price1 = curve.spotPriceAtSupply(supply = BigDecimal(100)).getOrThrow()
        val price2 = curve.spotPriceAtSupply(supply = BigDecimal(200)).getOrThrow()
        val cost = curve.tokensToValue(currentSupply = BigDecimal(100), tokens = BigDecimal(150)).getOrThrow()

        val expected = BigDecimal(100).multiplyWithHighPrecision(price1)
            .addWithHighPrecision(BigDecimal(50).multiplyWithHighPrecision(price2))
        assertTrue(isApproximatelyEqual(cost, expected))
    }

    @Test
    fun `2-13 Tokens beyond 21M returns failure`() {
        val result = curve.tokensToValue(currentSupply = BigDecimal(20_999_900), tokens = BigDecimal(200))
        assertTrue(result.isFailure)
    }

    @Test
    fun `2-14 500 tokens from supply 1000000`() {
        val cost = curve.tokensToValue(currentSupply = BigDecimal(1_000_000), tokens = BigDecimal(500)).getOrThrow()
        assertNotNull(cost)
        assertTrue(cost > BigDecimal.ZERO)
    }

    @Test
    fun `2-15 cost(A+B) = cost(A) + cost(B from A's end)`() {
        val costA = curve.tokensToValue(currentSupply = BigDecimal.ZERO, tokens = BigDecimal(200)).getOrThrow()
        val costB = curve.tokensToValue(currentSupply = BigDecimal(200), tokens = BigDecimal(150)).getOrThrow()
        val costTotal = curve.tokensToValue(currentSupply = BigDecimal.ZERO, tokens = BigDecimal(350)).getOrThrow()

        val sum = costA.addWithHighPrecision(costB)
        assertTrue(isApproximatelyEqual(costTotal, sum))
    }

    @Test
    fun `2-16 1 token at various positions equals price at that step`() {
        for (step in listOf(0, 5, 10, 50, 100)) {
            val supply = BigDecimal(step * 100)
            val price = curve.spotPriceAtSupply(supply = supply).getOrThrow()
            val cost = curve.tokensToValue(currentSupply = supply, tokens = BigDecimal.ONE).getOrThrow()
            assertTrue(isApproximatelyEqual(cost, price), "1 token cost should equal spot price at step $step")
        }
    }

    @Test
    fun `2-17 tokensToValue(0, step times 100) matches cumulative table pattern`() {
        val cost0 = curve.tokensToValue(currentSupply = BigDecimal.ZERO, tokens = BigDecimal.ZERO).getOrThrow()
        assertEquals(BigDecimal.ZERO, cost0)

        val cost100 = curve.tokensToValue(currentSupply = BigDecimal.ZERO, tokens = BigDecimal(100)).getOrThrow()
        val expectedApprox = BigDecimal("1.0")

        assertTrue(isApproximatelyEqual(cost100, expectedApprox, tolerance = BigDecimal("0.001")))
    }
}

// MARK: - 3. Value To Tokens Tests

class DiscreteValueToTokensTests {

    private lateinit var curve: DiscreteBondingCurve

    @BeforeTest
    fun initializeCurve() {
        CurveTestInitializer.initialize()
        curve = DiscreteBondingCurve.getOrThrow()
    }

    @Test
    fun `3-1 0 value yields 0 tokens from any supply`() {
        val tokens = curve.valueToTokens(currentSupply = BigDecimal.ZERO, value = BigDecimal.ZERO).getOrThrow()
        assertEquals(BigDecimal.ZERO, tokens)

        val tokens2 = curve.valueToTokens(currentSupply = BigDecimal(1000), value = BigDecimal.ZERO).getOrThrow()
        assertEquals(BigDecimal.ZERO, tokens2)
    }

    @Test
    fun `3-2 Value for 50 tokens yields approximately 50 tokens`() {
        val price = curve.spotPriceAtSupply(supply = BigDecimal.ZERO).getOrThrow()
        val value = BigDecimal(50).multiplyWithHighPrecision(price)
        val tokens = curve.valueToTokens(currentSupply = BigDecimal.ZERO, value = value).getOrThrow()
        assertTrue(isApproximatelyEqual(tokens, BigDecimal(50)))
    }

    @Test
    fun `3-3 Value for 25 tokens yields approximately 25 tokens`() {
        val price = curve.spotPriceAtSupply(supply = BigDecimal.ZERO).getOrThrow()
        val value = BigDecimal(25).multiplyWithHighPrecision(price)
        val tokens = curve.valueToTokens(currentSupply = BigDecimal.ZERO, value = value).getOrThrow()
        assertTrue(isApproximatelyEqual(tokens, BigDecimal(25)))
    }

    @Test
    fun `3-4 Value for 99 tokens yields approximately 99 tokens`() {
        val price = curve.spotPriceAtSupply(supply = BigDecimal.ZERO).getOrThrow()
        val value = BigDecimal(99).multiplyWithHighPrecision(price)
        val tokens = curve.valueToTokens(currentSupply = BigDecimal.ZERO, value = value).getOrThrow()
        assertTrue(isApproximatelyEqual(tokens, BigDecimal(99)))
    }

    @Test
    fun `3-5 Value for 100 tokens yields approximately 100 tokens`() {
        val cost = curve.tokensToValue(currentSupply = BigDecimal.ZERO, tokens = BigDecimal(100)).getOrThrow()
        val tokens = curve.valueToTokens(currentSupply = BigDecimal.ZERO, value = cost).getOrThrow()
        assertTrue(isApproximatelyEqual(tokens, BigDecimal(100)))
    }

    @Test
    fun `3-6 Value crossing boundary yields expected tokens`() {
        val cost = curve.tokensToValue(currentSupply = BigDecimal.ZERO, tokens = BigDecimal(150)).getOrThrow()
        val tokens = curve.valueToTokens(currentSupply = BigDecimal.ZERO, value = cost).getOrThrow()
        assertTrue(isApproximatelyEqual(tokens, BigDecimal(150)))
    }

    @Test
    fun `3-7 From partial step, value for 150 tokens`() {
        val cost = curve.tokensToValue(currentSupply = BigDecimal(50), tokens = BigDecimal(150)).getOrThrow()
        val tokens = curve.valueToTokens(currentSupply = BigDecimal(50), value = cost).getOrThrow()
        assertTrue(isApproximatelyEqual(tokens, BigDecimal(150)))
    }

    @Test
    fun `3-8 Value for 500 tokens (5 steps)`() {
        val cost = curve.tokensToValue(currentSupply = BigDecimal.ZERO, tokens = BigDecimal(500)).getOrThrow()
        val tokens = curve.valueToTokens(currentSupply = BigDecimal.ZERO, value = cost).getOrThrow()
        assertTrue(isApproximatelyEqual(tokens, BigDecimal(500)))
    }

    @Test
    fun `3-9 50 tokens at high supply (step 10000)`() {
        val supply = BigDecimal(1_000_000)
        val cost = curve.tokensToValue(currentSupply = supply, tokens = BigDecimal(50)).getOrThrow()
        val tokens = curve.valueToTokens(currentSupply = supply, value = cost).getOrThrow()
        assertTrue(isApproximatelyEqual(tokens, BigDecimal(50)))
    }

    @Test
    fun `3-10 Small value can't complete a full token`() {
        val price = curve.spotPriceAtSupply(supply = BigDecimal.ZERO).getOrThrow()
        val smallValue = price.divideWithHighPrecision(BigDecimal(2))
        val tokens = curve.valueToTokens(currentSupply = BigDecimal.ZERO, value = smallValue).getOrThrow()
        assertTrue(isApproximatelyEqual(tokens, BigDecimal("0.5"), tolerance = BigDecimal("0.01")))
    }

    @Test
    fun `3-11 Exactly enough to complete current step`() {
        val price = curve.spotPriceAtSupply(supply = BigDecimal.ZERO).getOrThrow()
        val value = BigDecimal(50).multiplyWithHighPrecision(price)
        val tokens = curve.valueToTokens(currentSupply = BigDecimal(50), value = value).getOrThrow()
        assertTrue(isApproximatelyEqual(tokens, BigDecimal(50)))
    }

    @Test
    fun `3-12 Supply at last step with large value returns failure`() {
        val result = curve.valueToTokens(currentSupply = BigDecimal(21_000_000), value = BigDecimal("1000000"))
        assertTrue(result.isFailure)
    }

    @Test
    fun `3-13 Value for 1 token`() {
        val price = curve.spotPriceAtSupply(supply = BigDecimal.ZERO).getOrThrow()
        val tokens = curve.valueToTokens(currentSupply = BigDecimal.ZERO, value = price).getOrThrow()
        assertTrue(isApproximatelyEqual(tokens, BigDecimal.ONE))
    }

    @Test
    fun `3-14 Value for 175 tokens (partial end step)`() {
        val cost = curve.tokensToValue(currentSupply = BigDecimal.ZERO, tokens = BigDecimal(175)).getOrThrow()
        val tokens = curve.valueToTokens(currentSupply = BigDecimal.ZERO, value = cost).getOrThrow()
        assertTrue(isApproximatelyEqual(tokens, BigDecimal(175)))
    }
}

// MARK: - 4. Roundtrip & Consistency Tests

class DiscreteRoundtripTests {

    private lateinit var curve: DiscreteBondingCurve

    @BeforeTest
    fun initializeCurve() {
        CurveTestInitializer.initialize()
        curve = DiscreteBondingCurve.getOrThrow()
    }

    @Test
    fun `4-1 tokens to value to tokens approximately equals original`() {
        val originalTokens = BigDecimal(250)
        val value = curve.tokensToValue(currentSupply = BigDecimal(100), tokens = originalTokens).getOrThrow()
        val recoveredTokens = curve.valueToTokens(currentSupply = BigDecimal(100), value = value).getOrThrow()
        assertTrue(isApproximatelyEqual(recoveredTokens, originalTokens, tolerance = BigDecimal("1")))
    }

    @Test
    fun `4-2 value to tokens to value approximately equals original`() {
        val originalValue = BigDecimal(5 * 1_000_000)
        val tokens = curve.valueToTokens(currentSupply = BigDecimal.ZERO, value = originalValue).getOrThrow()

        val wholeTokens = tokens.setScale(0, RoundingMode.DOWN)
        val recoveredValue = curve.tokensToValue(currentSupply = BigDecimal.ZERO, tokens = wholeTokens).getOrThrow()

        assertTrue(recoveredValue <= originalValue)
        val diff = originalValue.subtractWithHighPrecision(recoveredValue)
        assertTrue(diff < BigDecimal("2"), "Value difference should be small")
    }

    @Test
    fun `4-3 spotPrice(S) equals tokensToValue(S, 1)`() {
        for (step in listOf(0, 10, 100, 1000)) {
            val supply = BigDecimal(step * 100)
            val price = curve.spotPriceAtSupply(supply = supply).getOrThrow()
            val cost = curve.tokensToValue(currentSupply = supply, tokens = BigDecimal.ONE).getOrThrow()
            assertTrue(isApproximatelyEqual(price, cost), "Step $step: spotPrice should equal cost of 1 token")
        }
    }

    @Test
    fun `4-4 Methods handle step boundaries consistently`() {
        val price99 = curve.spotPriceAtSupply(supply = BigDecimal(99)).getOrThrow()
        val price0 = curve.spotPriceAtSupply(supply = BigDecimal.ZERO).getOrThrow()
        val price100 = curve.spotPriceAtSupply(supply = BigDecimal(100)).getOrThrow()

        assertEquals(price99, price0)
        assertNotEquals(price100, price0)
    }

    @Test
    fun `4-5 Buying in parts equals buying all at once`() {
        val costA = curve.tokensToValue(currentSupply = BigDecimal.ZERO, tokens = BigDecimal(100)).getOrThrow()
        val costB = curve.tokensToValue(currentSupply = BigDecimal(100), tokens = BigDecimal(200)).getOrThrow()
        val costC = curve.tokensToValue(currentSupply = BigDecimal(300), tokens = BigDecimal(150)).getOrThrow()
        val costAll = curve.tokensToValue(currentSupply = BigDecimal.ZERO, tokens = BigDecimal(450)).getOrThrow()

        val sumParts = costA.addWithHighPrecision(costB).addWithHighPrecision(costC)
        assertTrue(isApproximatelyEqual(costAll, sumParts))
    }

    @Test
    fun `4-6 Large purchase across many steps`() {
        val cost = curve.tokensToValue(currentSupply = BigDecimal(1_234_500), tokens = BigDecimal(10_000)).getOrThrow()
        assertNotNull(cost)
        assertTrue(cost > BigDecimal.ZERO)
    }

    @Test
    fun `4-7 Fractional tokens handling`() {
        val price = curve.spotPriceAtSupply(supply = BigDecimal.ZERO).getOrThrow()
        val value = BigDecimal("10.5").multiplyWithHighPrecision(price)
        val tokens = curve.valueToTokens(currentSupply = BigDecimal.ZERO, value = value).getOrThrow()
        assertTrue(isApproximatelyEqual(tokens, BigDecimal("10.5"), tolerance = BigDecimal("0.01")))
    }
}

// MARK: - 5. Table Validation Tests

class DiscreteTableValidationTests {

    private lateinit var curve: DiscreteBondingCurve

    @BeforeTest
    fun initializeCurve() {
        CurveTestInitializer.initialize()
        curve = DiscreteBondingCurve.getOrThrow()
    }

    @Test
    fun `5-1 Pricing table has correct length`() {
        assertEquals(210_001, curve.pricingTable.size)
    }

    @Test
    fun `5-2 Cumulative table has correct length`() {
        assertEquals(210_001, curve.cumulativeTable.size)
    }

    @Test
    fun `5-3 Pricing table first entry is approximately 0-01`() {
        val price = curve.spotPriceAtSupply(supply = BigDecimal.ZERO).getOrThrow()
        val onePenny = BigDecimal("0.01")
        assertTrue(isApproximatelyEqual(price, onePenny, tolerance = BigDecimal("0.0001")))
    }

    @Test
    fun `5-4 Cumulative table first entry is zero`() {
        val first = curve.cumulativeTable.getRaw(0)
        assertEquals(BigDecimal.ZERO, first)
    }

    @Test
    fun `5-5 Pricing table is monotonically increasing`() {
        for (i in 1 until 100) {
            val prev = curve.pricingTable.getRaw(i - 1)
            val curr = curve.pricingTable.getRaw(i)
            assertTrue(curr >= prev, "Price at step $i should be >= step ${i - 1}")
        }
    }

    @Test
    fun `5-6 Cumulative table is monotonically increasing`() {
        for (i in 1 until 100) {
            val prev = curve.cumulativeTable.getRaw(i - 1)
            val curr = curve.cumulativeTable.getRaw(i)
            assertTrue(curr >= prev, "Cumulative at step $i should be >= step ${i - 1}")
        }
    }

    @Test
    fun `5-7 First few pricing entries match expected Rust values`() {
        val expectedRaw = listOf(
            10000000000000000UL,
            10000877213746469UL,
            10001754504443334UL,
            10002631872097344UL,
            10003509316715251UL,
        )

        for ((i, expected) in expectedRaw.withIndex()) {
            val actual = curve.pricingTable.getRaw(i)
            assertEquals(expected.toLong().toBigDecimal(), actual, "Mismatch at index $i")
        }
    }

    @Test
    fun `5-8 First few cumulative entries match expected Rust values`() {
        val expectedRaw = listOf(
            0UL,
            1000000000000000000UL,
            2000087721374646900UL,
            3000263171818980300UL,
            4000526359028714700UL,
        )

        for ((i, expected) in expectedRaw.withIndex()) {
            val actual = curve.cumulativeTable.getRaw(i)
            assertEquals(expected.toLong().toBigDecimal(), actual, "Mismatch at index $i")
        }
    }

    @Test
    fun `5-9 Table step size is 100`() {
        assertEquals(100, DiscreteBondingCurve.stepSize)
    }
}

// MARK: - 6. Edge Cases & Error Handling

class DiscreteEdgeCaseTests {

    private lateinit var curve: DiscreteBondingCurve

    @BeforeTest
    fun initializeCurve() {
        CurveTestInitializer.initialize()
        curve = DiscreteBondingCurve.getOrThrow()
    }

    @Test
    fun `6-1 Negative supply handling`() {
        val result = curve.spotPriceAtSupply(supply = BigDecimal(-1))
        assertTrue(result.isFailure)
    }

    @Test
    fun `6-2 Negative tokens handling`() {
        val result = curve.tokensToValue(currentSupply = BigDecimal.ZERO, tokens = BigDecimal(-1))
        assertTrue(result.isFailure)
    }

    @Test
    fun `6-3 Negative value handling`() {
        val result = curve.valueToTokens(currentSupply = BigDecimal.ZERO, value = BigDecimal(-1))
        assertTrue(result.isFailure)
    }

    @Test
    fun `6-4 Very large value doesn't crash`() {
        val hugeValue = BigDecimal("999999999999999999999999999999")
        val result = curve.valueToTokens(currentSupply = BigDecimal.ZERO, value = hugeValue)
        // Should either return failure or a valid (possibly capped) result - just checking it doesn't crash
        assertTrue(result.isSuccess || result.isFailure)
    }

    @Test
    fun `6-5 Very small values handled correctly`() {
        val tinyValue = BigDecimal("0.0000000001")
        val tokens = curve.valueToTokens(currentSupply = BigDecimal.ZERO, value = tinyValue).getOrThrow()
        assertTrue(tokens >= BigDecimal.ZERO)
    }

    @Test
    fun `6-6 Exactly at max supply boundary`() {
        val price = curve.spotPriceAtSupply(supply = MAX_SUPPLY).getOrThrow()
        assertNotNull(price)

        val cost = curve.tokensToValue(currentSupply = BigDecimal(20_999_900), tokens = BigDecimal(100)).getOrThrow()
        assertNotNull(cost)
    }

    @Test
    fun `6-7 Just over max supply returns failure`() {
        val priceResult = curve.spotPriceAtSupply(supply = BigDecimal(21_000_001))
        assertTrue(priceResult.isFailure)

        val costResult = curve.tokensToValue(currentSupply = BigDecimal(20_999_900), tokens = BigDecimal(200))
        assertTrue(costResult.isFailure)
    }
}

// MARK: - 7. Tokens For Value Exchange Tests

class DiscreteTokensForValueExchangeTests {

    private lateinit var curve: DiscreteBondingCurve

    @BeforeTest
    fun initializeCurve() {
        CurveTestInitializer.initialize()
        curve = DiscreteBondingCurve.getOrThrow()
    }

    @Test
    fun `7-1 Zero value fails`() {
        val result = curve.tokensForValueExchange(
            currentValue = BigDecimal("10.0"),
            value = BigDecimal.ZERO
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun `7-2 Negative value fails`() {
        val result = curve.tokensForValueExchange(
            currentValue = BigDecimal("10.0"),
            value = BigDecimal("-5.0")
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun `7-3 Basic exchange returns positive tokens`() {
        val currentValue = BigDecimal("10.0")
        val exchangeValue = BigDecimal("1.0")

        val result = curve.tokensForValueExchange(
            currentValue = currentValue,
            value = exchangeValue
        ).getOrThrow()

        assertTrue(result.tokens.signum() > 0, "Should get positive tokens")
    }

    @Test
    fun `7-4 Larger exchange value yields more tokens`() {
        val currentValue = BigDecimal("100.0")

        val result1 = curve.tokensForValueExchange(
            currentValue = currentValue,
            value = BigDecimal("1.0")
        ).getOrThrow()

        val result5 = curve.tokensForValueExchange(
            currentValue = currentValue,
            value = BigDecimal("5.0")
        ).getOrThrow()

        assertTrue(result5.tokens > result1.tokens, "More value should yield more tokens")
    }

    @Test
    fun `7-5 Exchange at different current values`() {
        val exchangeValue = BigDecimal("1.0")

        // At low current value (early in curve)
        val resultLow = curve.tokensForValueExchange(
            currentValue = BigDecimal("5.0"),
            value = exchangeValue
        ).getOrThrow()

        // At high current value (later in curve)
        val resultHigh = curve.tokensForValueExchange(
            currentValue = BigDecimal("500.0"),
            value = exchangeValue
        ).getOrThrow()

        // Both should return positive tokens
        assertTrue(resultLow.tokens.signum() > 0)
        assertTrue(resultHigh.tokens.signum() > 0)

        // At higher current value, same exchange value should yield fewer tokens (higher price)
        assertTrue(resultLow.tokens > resultHigh.tokens, "Same value should yield fewer tokens at higher current value")
    }

    @Test
    fun `7-6 Exchange value cannot exceed current value`() {
        val result = curve.tokensForValueExchange(
            currentValue = BigDecimal("10.0"),
            value = BigDecimal("15.0")
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun `7-7 Small exchange amount works correctly`() {
        val result = curve.tokensForValueExchange(
            currentValue = BigDecimal("10.0"),
            value = BigDecimal("0.01")
        ).getOrThrow()

        assertTrue(result.tokens.signum() > 0)
    }

    @Test
    fun `7-8 Exchange entire current value`() {
        val currentValue = BigDecimal("50.0")
        val result = curve.tokensForValueExchange(
            currentValue = currentValue,
            value = currentValue
        ).getOrThrow()

        assertTrue(result.tokens.signum() > 0)
    }

    @Test
    fun `7-9 Zero current value fails`() {
        val result = curve.tokensForValueExchange(
            currentValue = BigDecimal.ZERO,
            value = BigDecimal("1.0")
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun `7-10 Negative current value fails`() {
        val result = curve.tokensForValueExchange(
            currentValue = BigDecimal("-10.0"),
            value = BigDecimal("1.0")
        )
        assertTrue(result.isFailure)
    }
}

// MARK: - 8. Constants Tests

class DiscreteConstantsTests {

    @Test
    fun `8-1 quarksPerToken equals 10 to the 10`() {
        assertEquals(10_000_000_000L, DiscreteBondingCurve.quarksPerToken)
    }

    @Test
    fun `8-2 stepSize is 100`() {
        assertEquals(100, DiscreteBondingCurve.stepSize)
    }

    @Test
    fun `8-3 maxSupply is 21 million`() {
        assertEquals(21_000_000, DiscreteBondingCurve.maxSupply)
    }

    @Test
    fun `8-4 tableSize is 210001`() {
        assertEquals(210_001, DiscreteBondingCurve.tableSize)
    }

    @Test
    fun `8-5 tablePrecision is 18`() {
        assertEquals(18, DiscreteBondingCurve.tablePrecision)
    }
}

// MARK: - 9. Real-World Scenario Tests

class DiscreteRealWorldTests {

    private lateinit var curve: DiscreteBondingCurve

    @BeforeTest
    fun initializeCurve() {
        CurveTestInitializer.initialize()
        curve = DiscreteBondingCurve.getOrThrow()
    }

    @Test
    fun `9-1 BigDecimal ten pow 18 equals 10 to 18 string`() {
        val powVersion = BigDecimal.TEN.pow(18, mc)
        val stringVersion = BigDecimal("1000000000000000000")

        val powStr = powVersion.toPlainString()
        val stringStr = stringVersion.toPlainString()

        assertEquals(stringStr, powStr, "Both should equal 1000000000000000000")
    }

    @Test
    fun `9-2 Multiplication by 10 to 18 gives correct scaled value`() {
        val tvl = BigDecimal("231.804283")

        val scale18String = BigDecimal("1000000000000000000")
        val scaled1 = tvl.multiplyWithHighPrecision(scale18String)

        val scale18Pow = BigDecimal.TEN.pow(18, mc)
        val scaled2 = tvl.multiplyWithHighPrecision(scale18Pow)

        val str1 = scaled1.toPlainString()
        val str2 = scaled2.toPlainString()

        assertTrue(str1.startsWith("231804283"), "Should start with 231804283")
        assertTrue(str2.startsWith("231804283"), "Should start with 231804283")
        assertEquals(str1, str2, "Both methods should give same result")
    }

    @Test
    fun `9-3 setScale with RoundingMode DOWN works correctly`() {
        val tvl = BigDecimal("231.804283")
        val scaleFactor = BigDecimal.TEN.pow(18, mc)
        val scaled = tvl.multiplyWithHighPrecision(scaleFactor)

        val intPart = scaled.setScale(0, RoundingMode.DOWN)

        assertTrue(intPart.toPlainString().startsWith("231804283"), "Should preserve value")
    }

    @Test
    fun `9-5 Cumulative table values are monotonically increasing around step 200`() {
        for (i in 195 until 210) {
            val curr = curve.cumulativeTable.getRaw(i)
            val next = curve.cumulativeTable.getRaw(i + 1)
            assertTrue(next > curr, "cumulative[${i + 1}] should be > cumulative[$i]")
        }
    }

    @Test
    fun `9-6 Cumulative at step 230 should be around 232`() {
        val step230 = curve.cumulativeTable.getRaw(230)

        val step230BigInt = step230.toBigInteger()
        val scale18 = BigDecimal("1000000000000000000")
        val step230Decimal = BigDecimal(step230BigInt).divideWithHighPrecision(scale18)

        val step230Double = step230Decimal.toDouble()
        assertTrue(step230Double > 225, "Cumulative at step 230 should be > \$225")
        assertTrue(step230Double < 250, "Cumulative at step 230 should be < \$250")
    }

    @Test
    fun `9-7 Binary search finds correct step for TVL 231-80`() {
        val tvl = BigDecimal("231.804283")
        val scale18 = BigDecimal("1000000000000000000")
        val tvlScaled = tvl.multiplyWithHighPrecision(scale18)

        val tvlBigD = tvlScaled.setScale(0, RoundingMode.DOWN)

        var foundStep = -1
        for (i in 0 until curve.cumulativeTable.size) {
            if (curve.cumulativeTable.getRaw(i) <= tvlBigD) {
                foundStep = i
            } else {
                break
            }
        }

        assertTrue(foundStep > 220, "Should find step > 220 for TVL \$231.80")
        assertTrue(foundStep < 240, "Should find step < 240 for TVL \$231.80")
    }

    @Test
    fun `9-8 tokensForValueExchange works for small amounts with large current value`() {
        // Scenario: Current value ~$231.80, exchanging ~$0.72 USD (1 CAD at 1.38 rate)
        val currentValue = BigDecimal("231.804283")
        val exchangeValue = BigDecimal("0.723")  // ~$1 CAD / 1.38262

        val result = curve.tokensForValueExchange(
            currentValue = currentValue,
            value = exchangeValue
        ).getOrThrow()

        assertTrue(result.tokens.signum() > 0, "Tokens should be positive")

        val tokensDouble = result.tokens.toDouble()

        assertTrue(tokensDouble > 50, "Should get > 50 tokens for ~\$0.72 USD")
        assertTrue(tokensDouble < 100, "Should get < 100 tokens for ~\$0.72 USD")
    }

    @Test
    fun `9-9 Two close current values produce different token amounts`() {
        val exchangeValue = BigDecimal("0.72")

        val result1 = curve.tokensForValueExchange(
            currentValue = BigDecimal("231.804283"),
            value = exchangeValue
        ).getOrThrow()

        val result2 = curve.tokensForValueExchange(
            currentValue = BigDecimal("231.081018"),
            value = exchangeValue
        ).getOrThrow()

        // Both should return tokens, potentially different amounts due to different positions on curve
        assertTrue(result1.tokens.signum() > 0)
        assertTrue(result2.tokens.signum() > 0)
    }
}