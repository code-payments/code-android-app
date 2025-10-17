package com.flipcash.libs.currency.math

import com.flipcash.libs.currency.math.internal.DefaultMintMaxQuarkSupply
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals

class EstimationTests {

    @Test
    fun `estimate current price`() {
        val curve = ExponentialCurve.getOrThrow()
        val priceForZero = Estimator.currentPriceFor(0).getOrThrow()
        val priceForAll = Estimator.currentPriceFor(DefaultMintMaxQuarkSupply).getOrThrow()

        val spot0 = curve.spotPriceAtSupply(BigDecimal.ZERO).getOrThrow()
        val spotMax = curve.spotPriceAtSupply(MAX_SUPPLY).getOrThrow()

        assertEquals(spot0, priceForZero)
        assertEquals(spotMax, priceForAll)
    }

    @Test
    fun `estimate value exchange`() {
        val quarks = Estimator.valueExchangeAsQuarks(
            valueInQuarks = 5_000_000,                 // $5,
            currentSupplyInQuarks =  7232649000000000, // 723,264.9 tokens
            mintDecimals = 6,
        ).getOrThrow()

        val expectedAmount = BigInteger("2651496281136")

        assertEquals(expectedAmount, quarks.toBigInteger())
    }

    @Test
    fun `estimate buys`() {
        val (received, fees) = Estimator.buy(
            amountInQuarks = 100_000_000,             // $100
            currentSupplyInQuarks = 7179502000000000, // 717,950.2 tokens
            mintDecimals = 6,
            feeBps = 0, // 0%
        ).getOrThrow()

        val expectedTotal = BigInteger("53147450513564")
        assertEquals( expectedTotal, (received + fees).toBigInteger())

        val (received2, fees2) = Estimator.buy(
            amountInQuarks = 100_000_000,             // $100
            currentSupplyInQuarks = 7179502000000000, // 717,950.2 tokens
            mintDecimals = 6,
            feeBps = 100, // 1%
        ).getOrThrow()

        assertEquals( expectedTotal, (received2 + fees2).toBigInteger())
    }

    @Test
    fun `estimate sells`() {
        val (received, fees) = Estimator.sell(
            amountInQuarks = 2651496281136,          // 265.1496281136 tokens
            currentValueInQuarks = 10100000000,      // $10100
            mintDecimals = 6,
            feeBps = 0, // 0%
        ).getOrThrow()

        val expectedTotal = BigInteger("5000000")
        assertEquals( expectedTotal, (received + fees).toBigInteger())

        val (received2, fees2) = Estimator.sell(
            amountInQuarks = 2651496281136,          // 265.1496281136 tokens
            currentValueInQuarks = 10100000000,      // $10100
            mintDecimals = 6,
            feeBps = 100, // 1%
        ).getOrThrow()

        assertEquals( expectedTotal, (received2 + fees2).toBigInteger())
    }
}