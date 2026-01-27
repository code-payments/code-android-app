package com.getcode.opencode.internal.exchange

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.getcode.opencode.controllers.CurrencyController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.internal.extensions.fromCode
import com.getcode.opencode.internal.model.LiveMintDataResponse
import com.getcode.opencode.model.financial.Currency
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Rate
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.Signature
import com.getcode.util.format
import com.getcode.util.locale.LocaleHelper
import com.getcode.util.resources.ResourceHelper
import com.getcode.util.resources.ResourceType
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import java.util.Date
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

internal class OpenCodeExchange @Inject constructor(
    private val currencyController: CurrencyController,
    private val resources: ResourceHelper,
    private val locale: LocaleHelper,
) : Exchange, DefaultLifecycleObserver {

    private var exchangeRatesStream: Job? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        scope.launch {
            val currencyCode = locale.getDefaultCurrencyName()
            balanceCurrency = CurrencyCode.tryValueOf(currencyCode)
        }
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        streamRates()
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        stopStreamingRates()
    }


    private val _balanceRate = MutableStateFlow(Rate.oneToOne)
    override val balanceRate
        get() = _balanceRate.value

    override fun observeBalanceRate(): Flow<Rate> = _balanceRate

    private val mints = MutableStateFlow<List<Mint>>(emptyList())

    override suspend fun setPreferredBalanceCurrency(currencyCode: CurrencyCode) {
        balanceCurrency = currencyCode
        rates.rateFor(currencyCode)?.let {
            _balanceRate.value = it
        }
    }

    override fun updateUserMints(mints: List<Mint>) {
        this.mints.value = mints
    }

    private val _entryRate = MutableStateFlow(Rate.oneToOne)
    override val entryRate: Rate
        get() = _entryRate.value

    override fun observeEntryRate(): Flow<Rate> = _entryRate

    override suspend fun setPreferredEntryCurrency(currencyCode: CurrencyCode) {
        entryCurrency = currencyCode
        rates.rateFor(currencyCode)?.let {
            _entryRate.value = it
        }
    }

    private var balanceCurrency: CurrencyCode? = null
    private var entryCurrency: CurrencyCode? = null

    private val _rates = MutableStateFlow(emptyMap<CurrencyCode, Rate>())
    private var rates = RatesBox(0, emptyMap())
        set(value) {
            field = value
            _rates.value = value.rates
        }

    override fun rates() = rates.rates
    override fun observeRates(): Flow<Map<CurrencyCode, Rate>> = _rates

    override suspend fun getCurrenciesWithRates(rates: Map<CurrencyCode, Rate>): List<Currency> =
        withContext(Dispatchers.Default) {
            return@withContext CurrencyCode.entries
                .mapNotNull { code ->
                    val rate = rates[code]?.fx ?: 0.0
                    getCurrencyWithRate(code.name, rate)
                }
        }

    override fun getCurrency(code: String): Currency? =
        CurrencyCode.tryValueOf(code)?.let { Currency.fromCode(it, resources) }

    override fun getCurrencyWithRate(code: String, rate: Double): Currency? =
        getCurrency(code)?.copy(rate = rate)

    override fun getFlagByCurrency(currencyCode: String?): Int? {
        currencyCode ?: return null
        return CurrencyCode.tryValueOf(currencyCode)?.let { currency ->
            currency.getRegion()?.name
        }?.let { regionName ->
            getFlag(regionName)
        }
    }

    override fun getFlag(countryCode: String): Int? {
        if (countryCode.isEmpty()) return null
        val resourceName = "ic_flag_${countryCode.lowercase()}"
        return resources.getIdentifier(
            resourceName,
            ResourceType.Drawable
        ).let { if (it == 0) null else it }
    }

    private fun stopStreamingRates() {
        exchangeRatesStream?.cancel()
    }

    override fun rateFor(currencyCode: CurrencyCode): Rate? = rates.rateFor(currencyCode)
    override fun proofFor(currencyCode: CurrencyCode): Signature? = rates.proofFor(currencyCode)

    override fun rateForUsd(): Rate = rates.rateForUsd()
    override fun proofForUsd(): Signature? = rates.proofFor(CurrencyCode.USD)

    override fun rateToUsd(from: CurrencyCode): Rate? {
        val fromRate = rates.rateFor(from) ?: return null

        return Rate(
            fx = 1 / fromRate.fx,
            currency = CurrencyCode.USD
        )
    }

    private fun streamRates() {
        stopStreamingRates()
        exchangeRatesStream = scope.launch {
            currencyController.streamLiveMintData(this, mints, tag = "exchange")
                .filterIsInstance<LiveMintDataResponse.ExchangeRates>()
                .collect { exchangeData ->
                    trace(tag = "Exchange", message = "Rates updated")
                    val associatedRates = exchangeData.rates.associateBy { it.rate.currency }
                    rates = RatesBox(
                        dateMillis = Clock.System.now().toEpochMilliseconds(),
                        rates = associatedRates.mapValues { it.value.rate },
                        proofs = associatedRates.mapValues { it.value.signature }
                    )
                    updateRates()
                }
        }
    }

    private fun updateRates() {
        if (rates.isEmpty) {
            return
        }

        val balanceRate = balanceCurrency?.let { rates.rateFor(it) }
        val balanceChanged = _balanceRate.value != balanceRate
        if (balanceChanged) {
            _balanceRate.value = if (balanceRate != null) {
                trace(
                    tag = "Background",
                    message = "Updated the local currency: $balanceCurrency, " +
                            "Staleness ${(System.currentTimeMillis() - rates.dateMillis).minutes} mins, " +
                            "Date: ${Date(rates.dateMillis)}",
                    type = TraceType.Process
                )
                balanceRate
            } else {
                trace(
                    tag = "Background",
                    message = "local:: Rate for $balanceCurrency not found. Defaulting to USD.",
                    type = TraceType.Process
                )
                rates.rateForUsd()
            }
        }


        val entryRate = entryCurrency?.let { rates.rateFor(it) }
        val entryChanged = _entryRate.value != entryRate
        if (entryChanged) {
            _entryRate.value = if (entryRate != null) {
                trace(
                    tag = "Background",
                    message = "Updated the entry currency: $entryCurrency, " +
                            "Staleness ${System.currentTimeMillis() - rates.dateMillis} ms, " +
                            "Date: ${Date(rates.dateMillis)}",
                    type = TraceType.Process
                )
                entryRate
            } else {
                trace(
                    tag = "Background",
                    message = "entry:: Rate for $entryCurrency not found. Defaulting to USD.",
                    type = TraceType.Process
                )
                rates.rateForUsd()
            }
        }

        if (balanceChanged || entryChanged) {
            trace(
                tag = "Background",
                message = "Updated rates",
                type = TraceType.Process,
                metadata = {
                    "date" to Instant.fromEpochMilliseconds(rates.dateMillis)
                        .format("yyyy-MM-dd HH:mm:ss")
                }
            )
        }
    }
}

private data class RatesBox(
    val dateMillis: Long,
    val rates: Map<CurrencyCode, Rate>,
    val proofs: Map<CurrencyCode, Signature> = emptyMap()
) {
    constructor(dateMillis: Long, rates: List<Rate>) : this(
        dateMillis,
        rates.associateBy { it.currency },
        emptyMap()
    )


    val isEmpty: Boolean
        get() = rates.isEmpty()

    fun rateFor(currencyCode: CurrencyCode): Rate? = rates[currencyCode]

    fun rateFor(currency: Currency): Rate? {
        val currencyCode = CurrencyCode.tryValueOf(currency.code)
        return currencyCode?.let { rates[it] }
    }

    fun proofFor(currencyCode: CurrencyCode): Signature? = proofs[currencyCode]

    fun rateForUsd(): Rate {
        return rates[CurrencyCode.USD] ?: Rate.oneToOne
    }
}