package com.getcode.opencode.internal.manager

import com.codeinc.opencode.gen.currency.v1.CurrencyService
import com.codeinc.opencode.gen.currency.v1.coreMintFiatExchangeRate
import com.codeinc.opencode.gen.currency.v1.verifiedCoreMintFiatExchangeRate
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.solana.keys.Mint
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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

    // region helpers

    private fun rateProto(
        code: String,
    ): CurrencyService.VerifiedCoreMintFiatExchangeRate {
        return verifiedCoreMintFiatExchangeRate {
            exchangeRate = coreMintFiatExchangeRate {
                currencyCode = code
            }
        }
    }

    // endregion
}
