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
            preferredCurrency = CurrencyCode.tryValueOf(currencyCode)
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        streamRates()
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        stopStreamingRates()
    }


    private val _preferredRate = MutableStateFlow(Rate.oneToOne)
    override val preferredRate: Rate
        get() = _preferredRate.value

    override fun observePreferredRate(): Flow<Rate> = _preferredRate

    private val mints = MutableStateFlow<List<Mint>>(emptyList())

    override suspend fun setPreferredCurrency(currencyCode: CurrencyCode) {
        preferredCurrency = currencyCode
        verifiedStateManager.rateFor(currencyCode)?.let {
            _preferredRate.value = it
        } ?: run {
            _preferredRate.value = Rate.oneToOne.copy(currency = currencyCode)
        }
    }

    override fun updateUserMints(mints: List<Mint>) {
        this.mints.value = mints
    }

    private var preferredCurrency: CurrencyCode? = null

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
        val rate = preferredCurrency?.let { rates[it] }
        val changed = _preferredRate.value != rate
        if (changed) {
            _preferredRate.value = if (rate != null) {
                trace(
                    tag = "Background",
                    message = "Updated the preferred currency: $preferredCurrency",
                    type = TraceType.Process
                )
                rate
            } else {
                trace(
                    tag = "Background",
                    message = "Rate for $preferredCurrency not found. Defaulting to USD.",
                    type = TraceType.Process
                )
                rates[CurrencyCode.USD] ?: Rate.oneToOne
            }

            trace(
                tag = "Background",
                message = "Updated rates",
                type = TraceType.Process,
            )
        }
    }
}