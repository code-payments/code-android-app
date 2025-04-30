package com.getcode.opencode.internal.exchange

import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.internal.extensions.fromCode
import com.getcode.opencode.internal.extensions.getClosestLocale
import com.getcode.opencode.internal.network.services.CurrencyService
import com.getcode.opencode.model.financial.Currency
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Rate
import com.getcode.services.opencode.R
import com.getcode.util.format
import com.getcode.util.locale.LocaleHelper
import com.getcode.util.resources.ResourceHelper
import com.getcode.util.resources.ResourceType
import com.getcode.utils.TraceType
import com.getcode.utils.network.retryable
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.Date
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

internal class OpenCodeExchange @Inject constructor(
    private val currencyService: CurrencyService,
    private val resources: ResourceHelper,
    private val locale: LocaleHelper,
) : Exchange, CoroutineScope by CoroutineScope(Dispatchers.IO) {

    private val _balanceRate = MutableStateFlow(Rate.oneToOne)
    override val balanceRate
        get() = _balanceRate.value

    override fun observeBalanceRate(): Flow<Rate> = _balanceRate

    override suspend fun setPreferredBalanceCurrency(currencyCode: CurrencyCode) {
        balanceCurrency = currencyCode
        fetchRatesIfNeeded()
        rates.rateFor(currencyCode)?.let {
            _balanceRate.value = it
        }
    }

    private val _entryRate = MutableStateFlow(Rate.oneToOne)
    override val entryRate: Rate
        get() = _entryRate.value

    override fun observeEntryRate(): Flow<Rate> = _entryRate

    override suspend fun setPreferredEntryCurrency(currencyCode: CurrencyCode) {
        entryCurrency = currencyCode
        fetchRatesIfNeeded()
        rates.rateFor(currencyCode)?.let {
            _entryRate.value = it
        }
    }

    private var rateDate: Long = System.currentTimeMillis()

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

    // Remember, the exchange rates date is the server-provided
    // date-of-rate and not the time the rate was fetched. It
    // might be reasonable for the server to return a date that
    // is dated 11 minutes or older.
    override val staleThreshold: Duration
        get() = 20.minutes

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
        if (currencyCode == "KIN") return R.drawable.ic_currency_kin
        if (currencyCode == "XAF") return R.drawable.ic_currency_xaf
        if (currencyCode == "XOF") return R.drawable.ic_currency_xof

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

    private val isStale: Boolean
        get() {
            if (rates.rates.isEmpty()) return true
            return System.currentTimeMillis() - rates.dateMillis > staleThreshold.inWholeMilliseconds
        }

    init {
        launch {
            val currencyCode = locale.getDefaultCurrencyName()
            balanceCurrency = CurrencyCode.tryValueOf(currencyCode)
            fetchRatesIfNeeded()
        }
    }

    override suspend fun fetchRatesIfNeeded() {
        if (isStale) {
            retryable(
                call = {
                    val now = Clock.System.now()
                    currencyService.getRates(now)
                        .onSuccess { rates ->
                            set(RatesBox(now.toEpochMilliseconds(), rates))
                        }
                }
            )
        }

        updateRates()
    }

    private suspend fun set(ratesBox: RatesBox) {
        rates = ratesBox
        rateDate = ratesBox.dateMillis

        setBalanceEntryCurrencyIfNeeded()
        updateRates()
    }

    private suspend fun setBalanceEntryCurrencyIfNeeded() {
        if (entryCurrency != null) {
            return
        }

        val localRegionCurrency = locale.getDefaultCurrencyName()
        val currency = CurrencyCode.tryValueOf(localRegionCurrency)
        entryCurrency = currency
    }

    override fun rateFor(currencyCode: CurrencyCode): Rate? = rates.rateFor(currencyCode)

    override fun rateForUsd(): Rate = rates.rateForUsd()
    override fun rateToUsd(from: CurrencyCode): Rate? {
        val fromRate = rates.rateFor(from) ?: return null

        return Rate(
            fx = 1 / fromRate.fx,
            currency = CurrencyCode.USD
        )
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
                            "Staleness ${System.currentTimeMillis() - rates.dateMillis} ms, " +
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

    private suspend fun getCurrency(code: CurrencyCode, scope: CoroutineScope): Currency {
        val resId = scope.async { getFlagByCurrency(code.name) }
        val currencyJava = scope.async { java.util.Currency.getInstance(code.name) }
        val locale = scope.async { code.getClosestLocale() }

        return Currency(
            code = currencyJava.await().currencyCode,
            name = currencyJava.await().displayName,
            resId = resId.await(),
            symbol = currencyJava.await().getSymbol(locale.await())
        )
    }
}

private data class RatesBox(val dateMillis: Long, val rates: Map<CurrencyCode, Rate>) {
    constructor(dateMillis: Long, rates: List<Rate>) : this(
        dateMillis,
        rates.associateBy { it.currency })

    val isEmpty: Boolean
        get() = rates.isEmpty()

    fun rateFor(currencyCode: CurrencyCode): Rate? = rates[currencyCode]

    fun rateFor(currency: Currency): Rate? {
        val currencyCode = CurrencyCode.tryValueOf(currency.code)
        return currencyCode?.let { rates[it] }
    }

    fun rateForUsd(): Rate {
        return rates[CurrencyCode.USD]?: Rate.oneToOne
    }
}