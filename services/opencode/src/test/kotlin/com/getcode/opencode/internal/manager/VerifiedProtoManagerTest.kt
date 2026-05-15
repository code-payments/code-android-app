package com.getcode.opencode.internal.manager

import app.cash.turbine.test
import com.codeinc.opencode.gen.currency.v1.CurrencyService
import com.codeinc.opencode.gen.currency.v1.coreMintFiatExchangeRate
import com.codeinc.opencode.gen.currency.v1.verifiedCoreMintFiatExchangeRate
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Rate
import com.getcode.solana.keys.Mint
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VerifiedProtoManagerTest {

    private lateinit var manager: VerifiedProtoManager

    @Before
    fun setUp() {
        manager = VerifiedProtoManager()
    }

    // region saveRates / getVerifiedStateFor

    @Test
    fun `getVerifiedStateFor returns null when no rates saved`() {
        assertNull(manager.getVerifiedStateFor(CurrencyCode.USD, Mint.usdf))
    }

    @Test
    fun `getVerifiedStateFor returns state after saving rate`() {
        manager.saveRates(listOf(rateProto("USD")))

        val state = manager.getVerifiedStateFor(CurrencyCode.USD, Mint.usdf)

        assertNotNull(state)
        assertNull(state.reserveProto)
    }

    @Test
    fun `getVerifiedStateFor returns null for unsaved currency`() {
        manager.saveRates(listOf(rateProto("USD")))

        assertNull(manager.getVerifiedStateFor(CurrencyCode.EUR, Mint.usdf))
    }

    @Test
    fun `saveRates skips invalid currency codes`() {
        manager.saveRates(listOf(rateProto("INVALID"), rateProto("USD")))

        assertNotNull(manager.getVerifiedStateFor(CurrencyCode.USD, Mint.usdf))
    }

    @Test
    fun `saveRates merges with existing data`() {
        manager.saveRates(listOf(rateProto("USD")))
        manager.saveRates(listOf(rateProto("EUR")))

        assertNotNull(manager.getVerifiedStateFor(CurrencyCode.USD, Mint.usdf))
        assertNotNull(manager.getVerifiedStateFor(CurrencyCode.EUR, Mint.usdf))
    }

    @Test
    fun `saveRates overwrites existing currency`() {
        val first = rateProto("USD")
        val second = rateProto("USD")

        manager.saveRates(listOf(first))
        manager.saveRates(listOf(second))

        val state = manager.getVerifiedStateFor(CurrencyCode.USD, Mint.usdf)
        assertNotNull(state)
        assertEquals(second, state.rateProto)
    }

    // endregion

    // region reset

    @Test
    fun `reset clears all cached data`() {
        manager.saveRates(listOf(rateProto("USD")))
        assertNotNull(manager.getVerifiedStateFor(CurrencyCode.USD, Mint.usdf))

        manager.reset()

        assertNull(manager.getVerifiedStateFor(CurrencyCode.USD, Mint.usdf))
    }

    // endregion

    // region rateFor

    @Test
    fun `rateFor returns null when no rates saved`() {
        assertNull(manager.rateFor(CurrencyCode.USD))
    }

    @Test
    fun `rateFor returns rate matching saved proto`() {
        manager.saveRates(listOf(rateProto("USD", fx = 1.23)))

        val rate = manager.rateFor(CurrencyCode.USD)

        assertNotNull(rate)
        assertEquals(1.23, rate.fx)
        assertEquals(CurrencyCode.USD, rate.currency)
    }

    @Test
    fun `rateFor returns null for unsaved currency`() {
        manager.saveRates(listOf(rateProto("USD", fx = 1.0)))

        assertNull(manager.rateFor(CurrencyCode.EUR))
    }

    // endregion

    // region observeRates

    @Test
    fun `observeRates emits rates matching saved protos`() = runTest {
        manager.observeRates().test {
            // initial empty
            assertEquals(emptyMap(), awaitItem())

            manager.saveRates(listOf(rateProto("USD", fx = 1.0), rateProto("EUR", fx = 0.85)))

            val rates = awaitItem()
            assertEquals(2, rates.size)
            assertEquals(1.0, rates[CurrencyCode.USD]?.fx)
            assertEquals(0.85, rates[CurrencyCode.EUR]?.fx)
        }
    }

    @Test
    fun `observeRates updates when new rates are saved`() = runTest {
        manager.observeRates().test {
            awaitItem() // initial empty

            manager.saveRates(listOf(rateProto("USD", fx = 1.0)))
            val first = awaitItem()
            assertEquals(1.0, first[CurrencyCode.USD]?.fx)

            manager.saveRates(listOf(rateProto("USD", fx = 1.5)))
            val second = awaitItem()
            assertEquals(1.5, second[CurrencyCode.USD]?.fx)
        }
    }

    // endregion

    // region rates

    @Test
    fun `rates returns empty map when no rates saved`() {
        assertTrue(manager.rates().isEmpty())
    }

    @Test
    fun `rates returns all saved rates`() {
        manager.saveRates(listOf(rateProto("USD", fx = 1.0), rateProto("EUR", fx = 0.85)))

        val rates = manager.rates()
        assertEquals(2, rates.size)
        assertEquals(Rate(fx = 1.0, currency = CurrencyCode.USD), rates[CurrencyCode.USD])
        assertEquals(Rate(fx = 0.85, currency = CurrencyCode.EUR), rates[CurrencyCode.EUR])
    }

    // endregion

    // region helpers

    private fun rateProto(
        code: String,
        fx: Double = 0.0,
    ): CurrencyService.VerifiedCoreMintFiatExchangeRate {
        return verifiedCoreMintFiatExchangeRate {
            exchangeRate = coreMintFiatExchangeRate {
                currencyCode = code
                exchangeRate = fx
            }
        }
    }

    // endregion
}
