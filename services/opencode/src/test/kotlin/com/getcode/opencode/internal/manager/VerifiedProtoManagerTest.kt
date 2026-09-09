package com.getcode.opencode.internal.manager

import app.cash.turbine.test
import com.codeinc.opencode.gen.common.v1.solanaAccountId
import com.codeinc.opencode.gen.currency.v1.OcpCurrencyService
import com.codeinc.opencode.gen.currency.v1.coreMintFiatExchangeRate
import com.codeinc.opencode.gen.currency.v1.launchpadCurrencyReserveState
import com.codeinc.opencode.gen.currency.v1.verifiedCoreMintFiatExchangeRate
import com.codeinc.opencode.gen.currency.v1.verifiedLaunchpadCurrencyReserveState
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Rate
import com.getcode.solana.keys.Mint
import com.google.protobuf.ByteString
import com.google.protobuf.Timestamp
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class VerifiedProtoManagerTest {

    private class FakeClock(var current: Instant) : Clock {
        override fun now(): Instant = current
    }

    private val start = Instant.fromEpochSeconds(1_757_433_600)
    private lateinit var clock: FakeClock
    private lateinit var manager: VerifiedProtoManager

    @Before
    fun setUp() {
        clock = FakeClock(start)
        manager = VerifiedProtoManager(clock)
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

    // region expiry

    @Test
    fun `getVerifiedStateFor returns state while the rate is at most 13 minutes old`() {
        manager.saveRates(listOf(rateProto("USD")))
        clock.current = start + 13.minutes

        assertNotNull(manager.getVerifiedStateFor(CurrencyCode.USD, Mint.usdf))
    }

    @Test
    fun `getVerifiedStateFor returns null once the rate is older than 13 minutes`() {
        manager.saveRates(listOf(rateProto("USD")))
        clock.current = start + 13.minutes + 1.seconds

        assertNull(manager.getVerifiedStateFor(CurrencyCode.USD, Mint.usdf))
    }

    @Test
    fun `getVerifiedStateFor evicts an expired rate from the cache`() {
        manager.saveRates(listOf(rateProto("USD", fx = 1.0)))
        clock.current = start + 14.minutes

        manager.getVerifiedStateFor(CurrencyCode.USD, Mint.usdf)

        assertNull(manager.rateFor(CurrencyCode.USD))
    }

    @Test
    fun `getVerifiedStateFor drops an expired reserve state but keeps a fresh rate`() {
        val mint = Mint(List(32) { 7.toByte() })
        manager.saveRates(listOf(rateProto("USD")))
        manager.saveReserveStates(listOf(reserveProto(mint)))
        clock.current = start + 10.minutes
        manager.saveRates(listOf(rateProto("USD")))
        clock.current = start + 14.minutes

        val state = manager.getVerifiedStateFor(CurrencyCode.USD, mint)

        assertNotNull(state)
        assertNull(state.reserveProto)
    }

    // endregion

    // region helpers

    private fun rateProto(
        code: String,
        fx: Double = 0.0,
        timestamp: Instant = clock.now(),
    ): OcpCurrencyService.VerifiedCoreMintFiatExchangeRate {
        return verifiedCoreMintFiatExchangeRate {
            exchangeRate = coreMintFiatExchangeRate {
                currencyCode = code
                exchangeRate = fx
                this.timestamp = timestamp.toProto()
            }
        }
    }

    private fun reserveProto(
        mint: Mint,
        timestamp: Instant = clock.now(),
    ): OcpCurrencyService.VerifiedLaunchpadCurrencyReserveState {
        return verifiedLaunchpadCurrencyReserveState {
            reserveState = launchpadCurrencyReserveState {
                this.mint = solanaAccountId { value = ByteString.copyFrom(mint.bytes.toByteArray()) }
                this.timestamp = timestamp.toProto()
            }
        }
    }

    private fun Instant.toProto(): Timestamp = Timestamp.newBuilder()
        .setSeconds(epochSeconds)
        .setNanos(nanosecondsOfSecond)
        .build()

    // endregion
}
