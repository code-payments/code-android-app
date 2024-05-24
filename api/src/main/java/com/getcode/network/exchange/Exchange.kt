package com.getcode.network.exchange

import android.annotation.SuppressLint
import com.getcode.db.Database
import com.getcode.model.Currency
import com.getcode.model.CurrencyCode
import com.getcode.model.PrefsString
import com.getcode.model.Rate
import com.getcode.network.api.CurrencyApi
import com.getcode.network.core.NetworkOracle
import com.getcode.network.repository.PrefRepository
import com.getcode.utils.ErrorUtils
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.util.Date
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.convert
import kotlin.time.Duration.Companion.minutes
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime

interface Exchange {
    val localRate: Rate
    fun observeLocalRate(): Flow<Rate>

    val entryRate: Rate
    fun observeEntryRate(): Flow<Rate>

    fun rates(): Map<CurrencyCode, Rate>
    fun observeRates(): Flow<Map<CurrencyCode, Rate>>

    suspend fun fetchRatesIfNeeded()

    fun rateFor(currencyCode: CurrencyCode): Rate?

    fun rateForUsd(): Rate?
}

class ExchangeNull : Exchange {
    override val localRate: Rate
        get() = Rate.oneToOne

    override val entryRate: Rate
        get() = Rate.oneToOne


    override fun observeLocalRate(): Flow<Rate> {
        return emptyFlow()
    }

    override fun observeEntryRate(): Flow<Rate> {
        return emptyFlow()
    }

    override fun rates(): Map<CurrencyCode, Rate> {
        return emptyMap()
    }

    override fun observeRates(): Flow<Map<CurrencyCode, Rate>> {
        return emptyFlow()
    }

    override suspend fun fetchRatesIfNeeded() = Unit

    override fun rateFor(currencyCode: CurrencyCode): Rate? {
        return null
    }

    override fun rateForUsd(): Rate? {
        return null
    }

}

class CodeExchange @Inject constructor(
    private val currencyApi: CurrencyApi,
    private val networkOracle: NetworkOracle,
    prefs: PrefRepository,
    private val preferredCurrency: suspend () -> Currency?,
    private val defaultCurrency: suspend () -> Currency?,
) : Exchange, CoroutineScope by CoroutineScope(Dispatchers.IO) {

    private val db = Database.getInstance()

    private var _entryRate = MutableStateFlow(Rate.oneToOne)
    override val entryRate: Rate
        get() = _entryRate.value

    override fun observeEntryRate(): Flow<Rate> = _entryRate

    private val _localRate = MutableStateFlow(Rate.oneToOne)
    override val localRate
        get() = _localRate.value

    override fun observeLocalRate(): Flow<Rate> = _localRate

    private var rateDate: Long = System.currentTimeMillis()

    private var localCurrency: CurrencyCode? = null
    private var entryCurrency: CurrencyCode? = null

    private val _rates = MutableStateFlow(emptyMap<CurrencyCode, Rate>())
    private var rates = RatesBox(0, emptyMap())
        set(value) {
            field = value
            _rates.value = value.rates
        }

    override fun rates() = rates.rates
    override fun observeRates(): Flow<Map<CurrencyCode, Rate>> = _rates

    private val isStale: Boolean
        get() {
            // Remember, the exchange rates date is the server-provided
            // date-of-rate and not the time the rate was fetched. It
            // might be reasonable for the server to return a date that
            // is dated 11 minutes or older.
            val threshold = 20.minutes.inWholeMilliseconds
            return System.currentTimeMillis() - rates.dateMillis > threshold
        }

    init {
        launch {
            localCurrency = CurrencyCode.tryValueOf(preferredCurrency()?.code.orEmpty())
            entryCurrency = CurrencyCode.tryValueOf(defaultCurrency()?.code.orEmpty())

            prefs.observeOrDefault(PrefsString.KEY_ENTRY_CURRENCY, "")
                .map { it.takeIf { it.isNotEmpty() } }
                .map { CurrencyCode.tryValueOf(it.orEmpty()) }
                .mapNotNull { preferred ->
                    preferred ?: CurrencyCode.tryValueOf(defaultCurrency()?.code.orEmpty())
                }.onEach { setEntryCurrency(it) }
                .launchIn(this)
        }

        launch {
            db?.exchangeDao()?.query()?.let { exchangeData ->
                val rates = exchangeData.map { Rate(it.fx, it.currency) }
                val dateMillis = exchangeData.minOf { it.synced }
                set(RatesBox(dateMillis = dateMillis, rates = rates))
            }

            fetchRatesIfNeeded()
        }

        prefs.observeOrDefault(PrefsString.KEY_LOCAL_CURRENCY, "")
            .map { it.takeIf { it.isNotEmpty() } }
            .map { CurrencyCode.tryValueOf(it.orEmpty()) }
            .mapNotNull { preferred ->
                preferred ?: CurrencyCode.tryValueOf(defaultCurrency()?.code.orEmpty())
            }.onEach { setLocalCurrency(it) }
            .launchIn(this)
    }

    override suspend fun fetchRatesIfNeeded() {
        if (isStale) {
            runCatching { fetchExchangeRates() }
                .onSuccess { (updatedRates, date) ->
                    db?.exchangeDao()?.insert(rates = updatedRates, syncedAt = date)
                    set(RatesBox(date, updatedRates))
                }.onFailure {
                    it.printStackTrace()
                }
        }
    }

    private suspend fun setEntryCurrency(currency: CurrencyCode) {
        entryCurrency = currency
        updateRates()
    }

    private suspend fun setLocalCurrency(currency: CurrencyCode) {
        localCurrency = currency
        updateRates()
    }

    private suspend fun set(ratesBox: RatesBox) {
        rates = ratesBox
        rateDate = ratesBox.dateMillis

        setLocalEntryCurrencyIfNeeded()
        updateRates()
    }

    private suspend fun setLocalEntryCurrencyIfNeeded() {
        if (entryCurrency != null) {
            return
        }

        val localRegionCurrency = defaultCurrency() ?: return
        val currency = CurrencyCode.tryValueOf(localRegionCurrency.code)
        entryCurrency = currency
    }

    override fun rateFor(currencyCode: CurrencyCode): Rate? = rates.rateFor(currencyCode)

    override fun rateForUsd(): Rate? = rates.rateForUsd()

    private suspend fun updateRates() {
        if (rates.isEmpty) {
            return
        }

        val localRate = localCurrency?.let { rates.rateFor(it) }
        _localRate.value = if (localRate != null) {
            trace(
                message = "Updated the local currency: $localCurrency, " +
                        "Staleness ${System.currentTimeMillis() - rates.dateMillis} ms, " +
                        "Date: ${Date(rates.dateMillis)}",
                type = TraceType.Silent
            )
            localRate
        } else {
            trace(
                message = "local:: Rate for $localCurrency not found. Defaulting to USD.",
                type = TraceType.Silent
            )
            rates.rateForUsd()!!
        }


        val entryRate = entryCurrency?.let { rates.rateFor(it) }
        _entryRate.value = if (entryRate != null) {
            trace(
                message = "Updated the entry currency: $entryCurrency, " +
                        "Staleness ${System.currentTimeMillis() - rates.dateMillis} ms, " +
                        "Date: ${Date(rates.dateMillis)}",
                type = TraceType.Silent
            )
            entryRate
        } else {
            trace(
                message = "entry:: Rate for $entryCurrency not found. Defaulting to USD.",
                type = TraceType.Silent
            )
            rates.rateForUsd()!!
        }

    }

    @OptIn(ExperimentalTime::class)
    @SuppressLint("CheckResult")
    private suspend fun fetchExchangeRates() = suspendCancellableCoroutine { cont ->
        Timber.d("fetching rates")
        currencyApi.getRates()
            .let { networkOracle.managedRequest(it) }
            .subscribe({ response ->
                val rates = response.ratesMap.mapNotNull { (key, value) ->
                    val currency = CurrencyCode.tryValueOf(key) ?: return@mapNotNull null
                    Rate(fx = value, currency = currency)
                }.toMutableList()

                if (rates.none { it.currency == CurrencyCode.KIN }) {
                    rates.add(Rate(fx = 1.0, currency = CurrencyCode.KIN))
                }

                cont.resume(
                    rates.toList() to convert(
                        value = response.asOf.seconds.toDouble(),
                        sourceUnit = DurationUnit.SECONDS,
                        targetUnit = DurationUnit.MILLISECONDS
                    ).toLong()
                )
            }, {
                ErrorUtils.handleError(it)
                cont.resume(emptyList<Rate>() to System.currentTimeMillis())
            })
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

    fun rateForUsd(): Rate? {
        return rates[CurrencyCode.USD]
    }
}