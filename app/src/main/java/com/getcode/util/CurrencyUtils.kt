package com.getcode.util

import com.getcode.App
import com.getcode.BuildConfig
import com.getcode.R
import com.getcode.model.Currency
import com.getcode.model.CurrencyCode
import com.getcode.model.RegionCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.*

object CurrencyUtils {

    val currencyKin = Currency("KIN", "Kin", R.drawable.ic_currency_kin, "K", 1.00)
    val currencies: List<Currency> by lazy { runBlocking {
        initCurrencies()
    } }

    private val currenciesMap: Map<String, Currency> by lazy {
        currencies.associateBy { it.code }
    }

    suspend fun getCurrenciesWithRates(rates: Map<String, Double>): List<Currency> = withContext(Dispatchers.Default) {
        return@withContext currencies
            .map { it.copy(rate = rates.getOrElse(it.code) { 0.0 }) }
    }

    suspend fun getCurrenciesMapWithRates(rates: Map<String, Double>): Map<String, Currency> {
        return getCurrenciesWithRates(rates).associateBy { it.code }
    }

    fun getCurrency(code: String) = currenciesMap[code]

    fun getCurrencyWithRate(code: String, rate: Double) = currenciesMap[code]?.copy(rate = rate)

    fun getFlagByCurrency(currencyCode: String?): Int? {
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

    fun getFlag(countryCode: String): Int? {
        if (countryCode.isEmpty()) return null
        val resourceName = "ic_flag_${countryCode.lowercase()}"
        return App.getInstance().resources.getIdentifier(
            resourceName,
            "drawable",
            BuildConfig.APPLICATION_ID
        ).let { if (it == 0) null else it }
    }

    private fun getLocale(region: RegionCode?): Locale {
        return Locale(Locale.getDefault().language, region?.name.orEmpty())
    }

    private suspend fun getCurrency(code: CurrencyCode, scope: CoroutineScope): Currency {
        if (code == CurrencyCode.KIN) return currencyKin

        val resId = scope.async { getFlagByCurrency(code.name) }
        val currencyJava = scope.async { java.util.Currency.getInstance(code.name) }
        val locale = scope.async { getLocale(code.getRegion()) }

        return Currency(
            code = currencyJava.await().currencyCode,
            name = currencyJava.await().displayName,
            resId = resId.await(),
            symbol = currencyJava.await().getSymbol(locale.await())
        )
    }

    /**
     * TODO
     * Instead of splitting this list roughly in half it would be faster
     * to chunk the list and build a concurrent hash map of currencies and sorting that
     */
    private suspend fun initCurrencies(): List<Currency> {
        val scope = CoroutineScope(Dispatchers.Default)

        val chunk1 = scope.async {
            CurrencyCode.values().copyOfRange(0, 75).map { currencyCode ->
            try {
                getCurrency(currencyCode, scope)
            } catch (_: Exception) {
                null
            }
        }
            .toMutableList()
            .filterNotNull()
        }

        val chunk2 = scope.async {
            CurrencyCode.values().copyOfRange(75, CurrencyCode.values().size).map { currencyCode ->
                try {
                    getCurrency(currencyCode, scope)
                } catch (_: Exception) {
                    null
                }
            }
                .toMutableList()
                .filterNotNull()
        }

        return chunk1.await().plus(chunk2.await()).sortedBy { it.name }
    }
}