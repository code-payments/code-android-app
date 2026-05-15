package com.getcode.opencode.internal.exchange

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.getcode.opencode.controllers.CurrencyController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.internal.extensions.fromCode
import com.getcode.opencode.internal.manager.VerifiedProtoManager
import com.getcode.opencode.model.financial.Currency
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Rate
import com.getcode.solana.keys.Mint
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal class OpenCodeExchange @Inject constructor(
    private val currencyController: CurrencyController,
    private val verifiedStateManager: VerifiedProtoManager,
    private val resources: ResourceHelper,
    private val locale: LocaleHelper,
) : Exchange, DefaultLifecycleObserver {

    private var ratesCollectionJob: Job? = null
    private var exchangeRatesStream: Job? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        scope.launch {
            val currencyCode = locale.getDefaultCurrencyName()
            balanceCurrency = CurrencyCode.tryValueOf(currencyCode)
        }
    }

    override fun onStart(owner: LifecycleOwner) {
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
        verifiedStateManager.rateFor(currencyCode)?.let {
            _balanceRate.value = it
        } ?: run {
            _balanceRate.value = Rate.oneToOne.copy(currency = currencyCode)
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
        verifiedStateManager.rateFor(currencyCode)?.let {
            _entryRate.value = it
        } ?: run {
            _entryRate.value = Rate.oneToOne.copy(currency = currencyCode)
        }
    }

    override fun resetEntryToBalance() {
        scope.launch { setPreferredEntryCurrency(balanceRate.currency) }
    }

    private var balanceCurrency: CurrencyCode? = null
    private var entryCurrency: CurrencyCode? = null

    override fun rates() = verifiedStateManager.rates()
    override fun observeRates(): Flow<Map<CurrencyCode, Rate>> = verifiedStateManager.observeRates()

    override suspend fun getCurrenciesWithRates(rates: Map<CurrencyCode, Rate>): List<Currency> =
        withContext(Dispatchers.Default) {
            return@withContext CurrencyCode.entries
                .filterNot { getFlagByCurrency(it.name) == null }
                .mapNotNull { code ->
                    val rate = rates[code]?.fx ?: 0.0
                    getCurrencyWithRate(code.name, rate)
                }.filterNot { it.code == it.name }
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
        ratesCollectionJob?.cancel()
    }

    override fun rateFor(currencyCode: CurrencyCode): Rate? = verifiedStateManager.rateFor(currencyCode)

    override fun rateForUsd(): Rate = verifiedStateManager.rateFor(CurrencyCode.USD) ?: Rate.oneToOne

    override fun rateToUsd(from: CurrencyCode): Rate? {
        val fromRate = verifiedStateManager.rateFor(from) ?: return null

        return Rate(
            fx = 1 / fromRate.fx,
            currency = CurrencyCode.USD
        )
    }

    private fun streamRates() {
        stopStreamingRates()
        // Start the stream so CurrencyService populates VerifiedProtoManager
        exchangeRatesStream = scope.launch {
            currencyController.streamLiveMintData(this, mints, tag = "exchange")
                .collect { /* stream is consumed to keep it alive; rates are saved to VerifiedProtoManager by CurrencyService */ }
        }
        // Observe rates from VerifiedProtoManager (single source of truth)
        ratesCollectionJob = scope.launch {
            verifiedStateManager.observeRates()
                .distinctUntilChanged()
                .collect { rates ->
                    if (rates.isEmpty()) return@collect
                    trace(tag = "Exchange", message = "Rates updated")
                    updateRates(rates)
                }
        }
    }

    private fun updateRates(rates: Map<CurrencyCode, Rate>) {
        val balanceRate = balanceCurrency?.let { rates[it] }
        val balanceChanged = _balanceRate.value != balanceRate
        if (balanceChanged) {
            _balanceRate.value = if (balanceRate != null) {
                trace(
                    tag = "Background",
                    message = "Updated the local currency: $balanceCurrency",
                    type = TraceType.Process
                )
                balanceRate
            } else {
                trace(
                    tag = "Background",
                    message = "local:: Rate for $balanceCurrency not found. Defaulting to USD.",
                    type = TraceType.Process
                )
                rates[CurrencyCode.USD] ?: Rate.oneToOne
            }
        }

        val entryRate = entryCurrency?.let { rates[it] }
        val entryChanged = _entryRate.value != entryRate
        if (entryChanged) {
            _entryRate.value = if (entryRate != null) {
                trace(
                    tag = "Background",
                    message = "Updated the entry currency: $entryCurrency",
                    type = TraceType.Process
                )
                entryRate
            } else {
                trace(
                    tag = "Background",
                    message = "entry:: Rate for $entryCurrency not found. Defaulting to USD.",
                    type = TraceType.Process
                )
                rates[CurrencyCode.USD] ?: Rate.oneToOne
            }
        }

        if (balanceChanged || entryChanged) {
            trace(
                tag = "Background",
                message = "Updated rates",
                type = TraceType.Process,
            )
        }
    }
}