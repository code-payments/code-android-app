package com.flipcash.libs.currency.math

import com.flipcash.libs.currency.math.internal.DefaultMintMaxQuarkSupply
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

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
            valueInQuarks = 10_000_000,                 // $10,
            currentValueInQuarks =  1_000_000_000,      // $1000
            mintDecimals = 6,
        ).getOrThrow()

        val expectedAmount = BigInteger("9197272362330")

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

        assertTrue { abs(expectedTotal.toDouble() - (received + fees).toDouble()) <= 1.0 }

        val (received2, fees2) = Estimator.buy(
            amountInQuarks = 100_000_000,             // $100
            currentSupplyInQuarks = 7179502000000000, // 717,950.2 tokens
            mintDecimals = 6,
            feeBps = 100, // 1%
        ).getOrThrow()

        assertTrue { abs(expectedTotal.toDouble() - (received2 + fees2).toDouble()) <= 1.0 }
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

    /**
     * This test generates a CSV table to verify the symmetry and accuracy of the buy, sell, and
     * value exchange estimations across a wide range of values. It simulates a scenario where an
     * initial amount (`valueLocked`) is used to buy tokens from a zero supply. Then, for various
     * sub-amounts (`paymentValue`), it calculates the corresponding token amount (`paymentQuarks`)
     * and immediately sells them back.
     *
     * The core assertion is that the value received from selling the tokens (`sellValue`) should be
     * almost identical to the original `paymentValue`, accounting for potential minor rounding
     * discrepancies (hence the tolerance of `1.0`).
     *
     * The test iterates through logarithmically scaled values for `valueLocked` and `paymentValue`,
     * starting from 10,000 and going up to 1 quadrillion quarks, ensuring the formulas hold
     * for both small and extremely large numbers.
     *
     */
    @Test
    fun `estimate csv table`() {
        val startValue = 10_000L
        val endValue = 1_000_000_000_000_000L

        println("value locked,total circulating supply,new circulating supply,payment value,payment quarks,sell value")
        var valueLocked = startValue
        while (valueLocked <= endValue) {
            val (totalCirculatingSupply, _) = Estimator.buy(
                amountInQuarks = valueLocked,
                currentSupplyInQuarks = 0L,
                mintDecimals = 6,
                feeBps = 0
            ).getOrThrow()

            var paymentValue = startValue
            while (paymentValue <= valueLocked) {
                val paymentQuarks = Estimator.valueExchangeAsQuarks(
                    valueInQuarks = paymentValue,
                    currentValueInQuarks = valueLocked,
                    mintDecimals = 6,
                ).getOrThrow()

                val (sellValue, _) = Estimator.sell(
                    amountInQuarks = paymentQuarks.toLong(),
                    currentValueInQuarks = valueLocked,
                    mintDecimals = 6,
                    feeBps = 0
                ).getOrThrow()

                val diff1 = paymentValue - sellValue.toLong()
                assertTrue("$paymentValue, $sellValue") { diff1 >= -1 && diff1 <= 1 }

                val (newCirculatingSupply, _) = Estimator.buy(
                    amountInQuarks = valueLocked - paymentValue,
                    currentSupplyInQuarks = 0,
                    mintDecimals = 6,
                    feeBps = 0,
                ).getOrThrow()

                val totalLong = totalCirculatingSupply.toLong()
                val newLong = newCirculatingSupply.toLong()
                val paymentQLong = paymentQuarks.toLong()
                val diff2 = totalLong - newLong - paymentQLong
                assertTrue("$totalCirculatingSupply, $newCirculatingSupply, $paymentQuarks") { diff2 >= -1 && diff2 <= 1 }

                println("$valueLocked,${totalCirculatingSupply.toLong()},${newCirculatingSupply.toLong()},$paymentValue,${paymentQuarks.toLong()},${sellValue.toLong()}")
                paymentValue *= 10L
            }
            valueLocked *= 10L
        }
    }
}