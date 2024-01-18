package com.getcode.network.exchange

import android.annotation.SuppressLint
import android.text.format.DateUtils
import com.getcode.db.AppDatabase
import com.getcode.db.Database
import com.getcode.model.Currency
import com.getcode.model.CurrencyCode
import com.getcode.model.Rate
import com.getcode.network.api.CurrencyApi
import com.getcode.network.core.NetworkOracle
import com.getcode.utils.ErrorUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.minutes

class Exchange @Inject constructor(
    private val currencyApi: CurrencyApi,
    private val networkOracle: NetworkOracle,
    private val defaultCurrency: () -> Currency?,
): CoroutineScope by CoroutineScope(Dispatchers.IO) {

    private val db = Database.getInstance()

    private var entryRate: Rate = Rate.oneToOne

    private val _localRate = MutableStateFlow(Rate.oneToOne)
    val localRate = _localRate.value

    fun observeLocalRate(): Flow<Rate> = _localRate

    private var rateDate: Long = System.currentTimeMillis()

    private var entryCurrency: CurrencyCode? = null

    private val _rates = MutableStateFlow(emptyMap<CurrencyCode, Rate>())
    private var rates = RatesBox(0, emptyMap())
        set(value) {
            field = value
            _rates.value = value.rates
        }

    fun rates() = rates.rates
    fun observeRates(): Flow<Map<CurrencyCode, Rate>> = _rates

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
            db?.exchangeDao()?.query()?.let { exchangeData ->
                val rates = exchangeData.map { Rate(it.fx, it.currency) }
                val dateMillis = exchangeData.minOf { it.synced }
                set(RatesBox(dateMillis = dateMillis, rates = rates))
            }

            fetchRatesIfNeeded()
        }
    }

    suspend fun fetchRatesIfNeeded() {
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

    fun set(currency: CurrencyCode) {
        entryCurrency = currency
        updateRates()
    }

    private fun set(ratesBox: RatesBox) {
        rates = ratesBox
        rateDate = ratesBox.dateMillis

        setLocalEntryCurrencyIfNeeded()
        updateRates()
    }

    private fun setLocalEntryCurrencyIfNeeded() {
        if (entryCurrency == null) {
            return
        }

        val localRegionCurrency = defaultCurrency() ?: return

        entryCurrency = CurrencyCode.tryValueOf(localRegionCurrency.code)
    }

    fun rateFor(currencyCode: CurrencyCode): Rate? = rates.rateFor(currencyCode)

    fun rateForUsd(): Rate? = rates.rateForUsd()

    private fun updateRates() {
        if (rates.isEmpty) {
            return
        }

        val localCurrency = defaultCurrency()
        val rate = localCurrency?.let { rates.rateFor(it) }
        _localRate.value = if (rate != null) {
            Timber.d("Updated the entry currency: $localCurrency, Staleness ${System.currentTimeMillis() - rates.dateMillis} ms, Date: ${Date(rates.dateMillis)}")
            rate
        } else {
            Timber.d("Rate for $localCurrency not found. Defaulting to USD.")
            rates.rateForUsd()!!
        }


        val entryRate = entryCurrency?.let { rates.rateFor(it) }
        this.entryRate = if (entryRate != null) {
            Timber.d("Updated the entry currency: $entryCurrency, Staleness ${System.currentTimeMillis() - rates.dateMillis} ms, Date: ${Date(rates.dateMillis)}")
            entryRate
        } else {
            Timber.d("Rate for $entryCurrency not found. Defaulting to USD.")
            rates.rateForUsd()!!
        }

    }

    @SuppressLint("CheckResult")
    private suspend fun fetchExchangeRates() = suspendCancellableCoroutine { cont ->
        Timber.d("fetching rates")
        currencyApi.getRates()
            .let { networkOracle.managedRequest(it) }
            .subscribe({ response ->
                Timber.d("rates=${response.ratesMap.count()}")
                val rates = response.ratesMap.mapNotNull { (key, value) ->
                    val currency = CurrencyCode.tryValueOf(key) ?: return@mapNotNull null
                    Rate(fx = value, currency = currency)
                }.toMutableList()

                if (rates.none { it.currency == CurrencyCode.KIN }) {
                    rates.add(Rate(fx = 1.0, currency = CurrencyCode.KIN))
                }
                cont.resume(rates.toList() to System.currentTimeMillis())
            }, {
                ErrorUtils.handleError(it)
                cont.resume(emptyList<Rate>() to System.currentTimeMillis())
            })
    }


}

private data class RatesBox(val dateMillis: Long, val rates: Map<CurrencyCode, Rate>) {
    constructor(dateMillis: Long, rates: List<Rate>): this(dateMillis, rates.associateBy { it.currency })

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