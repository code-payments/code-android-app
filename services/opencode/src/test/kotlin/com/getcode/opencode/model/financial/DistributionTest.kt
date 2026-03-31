package com.getcode.opencode.model.financial

import com.getcode.opencode.tests.generateRandomPublicKeyForTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class DistributionTest {

    @Test
    fun `Distribution stores destination and amount`() {
        val destination = generateRandomPublicKeyForTest()
        val amount = Fiat(fiat = 25.0)

        val distribution = Distribution(destination = destination, amount = amount)

        assertEquals(destination, distribution.destination)
        assertEquals(25.0, distribution.amount.decimalValue, 0.001)
    }

    @Test
    fun `Distribution with zero amount`() {
        val destination = generateRandomPublicKeyForTest()
        val distribution = Distribution(destination = destination, amount = Fiat.Zero)

        assertEquals(0L, distribution.amount.quarks)
    }

    @Test
    fun `two Distributions with same values are equal`() {
        val destination = generateRandomPublicKeyForTest()
        val amount = Fiat(fiat = 10.0)

        val d1 = Distribution(destination = destination, amount = amount)
        val d2 = Distribution(destination = destination, amount = amount)

        assertEquals(d1, d2)
    }

    @Test
    fun `two Distributions with different destinations are not equal`() {
        val amount = Fiat(fiat = 10.0)
        val d1 = Distribution(destination = generateRandomPublicKeyForTest(), amount = amount)
        val d2 = Distribution(destination = generateRandomPublicKeyForTest(), amount = amount)

        assertNotEquals(d1, d2)
    }

    @Test
    fun `two Distributions with different amounts are not equal`() {
        val destination = generateRandomPublicKeyForTest()
        val d1 = Distribution(destination = destination, amount = Fiat(fiat = 10.0))
        val d2 = Distribution(destination = destination, amount = Fiat(fiat = 20.0))

        assertNotEquals(d1, d2)
    }

    @Test
    fun `Distribution amount preserves currency`() {
        val destination = generateRandomPublicKeyForTest()
        val eurAmount = Fiat(fiat = 50.0, currencyCode = CurrencyCode.EUR)

        val distribution = Distribution(destination = destination, amount = eurAmount)

        assertEquals(CurrencyCode.EUR, distribution.amount.currencyCode)
    }

    @Test
    fun `Distribution copy changes only specified fields`() {
        val destination = generateRandomPublicKeyForTest()
        val original = Distribution(destination = destination, amount = Fiat(fiat = 10.0))
        val newAmount = Fiat(fiat = 20.0)

        val modified = original.copy(amount = newAmount)

        assertEquals(destination, modified.destination)
        assertEquals(20.0, modified.amount.decimalValue, 0.001)
    }
}
