package com.getcode.utils

import android.annotation.SuppressLint
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import com.getcode.libs.currency.R
import com.getcode.model.Currency
import com.getcode.model.CurrencyCode
import com.getcode.model.Rate
import com.getcode.model.RegionCode
import com.getcode.util.resources.ResourceHelper
import com.getcode.util.resources.ResourceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

val LocalCurrencyUtils: ProvidableCompositionLocal<CurrencyUtils?> = staticCompositionLocalOf { null }

@Singleton
class CurrencyUtils @Inject constructor(
   private val resources: ResourceHelper,
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

    fun getCurrency(code: String): Currency? {
        return currenciesMap[code.uppercase()]
    }

    fun getCurrencyWithRate(code: String, rate: Double) = currenciesMap[code.uppercase()]?.copy(rate = rate)

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
        return resources.getIdentifier(
            resourceName,
            ResourceType.Drawable
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

    private suspend fun initCurrencies(): List<Currency> {
        val scope = CoroutineScope(Dispatchers.Default)

        val currencyMap = ConcurrentHashMap<String, Currency>()

        val chunkSize = 25
        val chunks = CurrencyCode.entries.chunked(chunkSize)

        // Process each chunk asynchronously
        val jobs = chunks.map { chunk ->
            scope.async {
                chunk.forEach { currencyCode ->
                    try {
                        val currency = getCurrency(currencyCode, scope)
                        currencyMap[currency.name] = currency
                    } catch (_: Exception) {
                        // Handle exceptions if needed
                    }
                }
            }
        }

        // Wait for all jobs to complete
        jobs.awaitAll()

        // Sort the currencies by name
        return currencyMap.values.sortedBy { it.name }
    }
}