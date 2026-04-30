package com.getcode.opencode.internal.manager

import com.codeinc.opencode.gen.currency.v1.CurrencyService
import com.getcode.opencode.internal.network.extensions.toMint
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Rate
import com.getcode.solana.keys.Mint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Manages and caches verified state information fetched from the OpenCodeProtocol,
 * such as exchange rates and launchpad reserve states. This class acts as a
 * centralized in-memory store to provide quick access to this data across the
 * service, without having to pass it around in every function call.
 *
 * As a `@Singleton`, a single instance of this manager is shared throughout the
 * application lifecycle.
 */
@Singleton
class VerifiedProtoManager @Inject constructor() {

    private val TTL = 15.minutes

    /**
     * A [MutableStateFlow] holding the latest cached exchange rate data.
     * The data is stored in a map where the key is the [CurrencyCode] (e.g., "USD", "EUR")
     * and the value is the corresponding [com.getcode.opencode.model.financial.VerifiedResponseData.ExchangeRate] object,
     * which includes the rate and the timestamp of when it was fetched.
     */
    private val exchangeData = MutableStateFlow<Map<CurrencyCode, CurrencyService.VerifiedCoreMintFiatExchangeRate>>(emptyMap())
    /**
     * A [MutableStateFlow] holding a map of launchpad reserve states, keyed by their respective [Mint] address.
     *
     * This flow provides reactive access to the cached state of token reserves associated with launchpads,
     * allowing observers to be notified of updates. The data originates from the OpenCodeProtocol and is
     * populated via the `save` method.
     */
    private val reserveStates = MutableStateFlow<Map<Mint, CurrencyService.VerifiedLaunchpadCurrencyReserveState>>(emptyMap())

    fun saveRates(exchangeData: List<CurrencyService.VerifiedCoreMintFiatExchangeRate>) {
        val incoming = exchangeData.mapNotNull { data ->
            CurrencyCode.tryValueOf(data.exchangeRate.currencyCode)?.let { it to data }
        }.toMap()
        this.exchangeData.update { it + incoming }
    }

    fun saveReserveStates(reserveStates: List<CurrencyService.VerifiedLaunchpadCurrencyReserveState>) {
        val incoming = reserveStates.associateBy { it.reserveState.mint.toMint() }
        this.reserveStates.update { it + incoming }
    }

    /**
     * Derives [Rate] objects directly from the cached verified exchange rate protos.
     * This is the single source of truth for exchange rates — consumers should use
     * this instead of maintaining separate rate caches.
     */
    fun observeRates(): Flow<Map<CurrencyCode, Rate>> = exchangeData.map { map ->
        map.mapValues { (_, proto) ->
            Rate(
                fx = proto.exchangeRate.exchangeRate,
                currency = CurrencyCode.tryValueOf(proto.exchangeRate.currencyCode)
                    ?: CurrencyCode.USD
            )
        }
    }

    /**
     * Returns the current [Rate] for the given [currencyCode] directly from the
     * verified proto cache, or null if not available.
     */
    fun rateFor(currencyCode: CurrencyCode): Rate? {
        val proto = exchangeData.value[currencyCode] ?: return null
        return Rate(
            fx = proto.exchangeRate.exchangeRate,
            currency = CurrencyCode.tryValueOf(proto.exchangeRate.currencyCode)
                ?: CurrencyCode.USD
        )
    }

    fun rates(): Map<CurrencyCode, Rate> {
        return exchangeData.value.mapValues { (_, proto) ->
            Rate(
                fx = proto.exchangeRate.exchangeRate,
                currency = CurrencyCode.tryValueOf(proto.exchangeRate.currencyCode)
                    ?: CurrencyCode.USD
            )
        }
    }

    fun reset() {
        exchangeData.value = emptyMap()
        reserveStates.value = emptyMap()
    }

    private fun get(currencyCode: CurrencyCode): CurrencyService.VerifiedCoreMintFiatExchangeRate? {
        return exchangeData.value[currencyCode]
    }

    private fun getOrEvict(currencyCode: CurrencyCode): CurrencyService.VerifiedCoreMintFiatExchangeRate? {
        val now = Clock.System.now()
        val stored = get(currencyCode) ?: return null
        val ts = Instant.fromEpochSeconds(stored.exchangeRate.timestamp.seconds, stored.exchangeRate.timestamp.nanos)
        val expired = now - ts > TTL
        if (expired) {
            val updated = exchangeData.value.filterNot { it.key == currencyCode }
            exchangeData.update { updated }
        }

        return stored
    }

    private fun get(mint: Mint): CurrencyService.VerifiedLaunchpadCurrencyReserveState? {
        return reserveStates.value[mint]
    }

    private fun getOrEvict(mint: Mint): CurrencyService.VerifiedLaunchpadCurrencyReserveState? {
        val now = Clock.System.now()
        val stored = get(mint) ?: return null
        val ts = Instant.fromEpochSeconds(stored.reserveState.timestamp.seconds, stored.reserveState.timestamp.nanos)
        val expired = now - ts > TTL
        if (expired) {
            val updated = reserveStates.value.filterNot { it.key == mint }
            reserveStates.update { updated }
        }

        return stored
    }

    fun getVerifiedStateFor(currencyCode: CurrencyCode, mint: Mint): VerifiedState? {
        val exchangeRate = getOrEvict(currencyCode) ?: return null
        val reserveState = getOrEvict(mint)

        return VerifiedState(exchangeRate, reserveState)
    }
}

data class VerifiedState(
    val rateProto: CurrencyService.VerifiedCoreMintFiatExchangeRate,
    val reserveProto: CurrencyService.VerifiedLaunchpadCurrencyReserveState?,
)