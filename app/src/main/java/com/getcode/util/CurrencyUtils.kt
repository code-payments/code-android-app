package com.getcode.util

import android.annotation.SuppressLint
import android.content.Context
import com.getcode.BuildConfig
import com.getcode.R
import com.getcode.model.Currency
import com.getcode.model.CurrencyCode
import com.getcode.model.Rate
import com.getcode.model.RegionCode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton


// TODO: see if Exchange can absorb this?
@Singleton
class CurrencyUtils @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val currencies: List<Currency> by lazy { runBlocking {
        initCurrencies()
    } }

    private val currenciesMap: Map<String, Currency> by lazy {
        currencies.associateBy { it.code }
    }

    suspend fun getCurrenciesWithRates(rates: Map<CurrencyCode, Rate>): List<Currency> = withContext(Dispatchers.Default) {
        return@withContext currencies
            .mapNotNull {
                val code = CurrencyCode.tryValueOf(it.code) ?: return@mapNotNull null
                val rate = rates[code]?.fx ?: 0.0
                it.copy(rate = rate)
            }
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

    @SuppressLint("DiscouragedApi")
    fun getFlag(countryCode: String): Int? {
        if (countryCode.isEmpty()) return null
        val resourceName = "ic_flag_${countryCode.lowercase()}"
        return context.resources.getIdentifier(
            resourceName,
            "drawable",
            BuildConfig.APPLICATION_ID
        ).let { if (it == 0) null else it }
    }

    private fun getLocale(region: RegionCode?): Locale {
        return Locale(Locale.getDefault().language, region?.name.orEmpty())
    }

    private suspend fun getCurrency(code: CurrencyCode, scope: CoroutineScope): Currency {
        if (code == CurrencyCode.KIN) return Currency.Kin

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
            CurrencyCode.entries.toTypedArray().copyOfRange(0, 75).map { currencyCode ->
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
            CurrencyCode.entries.toTypedArray()
                .copyOfRange(75, CurrencyCode.entries.size).map { currencyCode ->
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