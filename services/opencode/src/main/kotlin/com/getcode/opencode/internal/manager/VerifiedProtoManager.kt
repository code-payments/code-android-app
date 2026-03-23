package com.getcode.opencode.internal.manager

import com.codeinc.opencode.gen.currency.v1.CurrencyService
import com.getcode.opencode.internal.network.extensions.toMint
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.solana.keys.Mint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

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

    fun reset() {
        exchangeData.value = emptyMap()
        reserveStates.value = emptyMap()
    }

    private fun get(currencyCode: CurrencyCode): CurrencyService.VerifiedCoreMintFiatExchangeRate? {
        return exchangeData.value[currencyCode]
    }

    private fun get(mint: Mint): CurrencyService.VerifiedLaunchpadCurrencyReserveState? {
        return reserveStates.value[mint]
    }

    fun getVerifiedStateFor(currencyCode: CurrencyCode, mint: Mint): VerifiedState? {
        val exchangeRate = get(currencyCode) ?: return null
        val reserveState = get(mint)

        return VerifiedState(exchangeRate, reserveState)
    }
}

data class VerifiedState(
    val rateProto: CurrencyService.VerifiedCoreMintFiatExchangeRate,
    val reserveProto: CurrencyService.VerifiedLaunchpadCurrencyReserveState?,
)