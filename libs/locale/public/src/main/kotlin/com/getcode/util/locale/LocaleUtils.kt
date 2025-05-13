package com.getcode.util.locale

import android.content.Context
import android.telephony.TelephonyManager
import com.getcode.model.CurrencyCode
import com.getcode.model.CurrencyCode.Companion
import com.getcode.model.RegionCode
import java.util.*

object LocaleUtils {
    fun getDefaultCountry(context: Context): String {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val networkCountryIso = tm.networkCountryIso.uppercase()
        val simCountryIso = tm.simCountryIso.uppercase()
        return simCountryIso.ifBlank { networkCountryIso }
    }

    fun getDefaultCurrency(context: Context): String {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val networkCountryIso = tm.networkCountryIso.uppercase()
        val simCountryIso = tm.simCountryIso.uppercase()
        val localCountry = context.resources.configuration.locale
        val isRoaming = tm.isNetworkRoaming

        val networkCountryIsoCurrency = if (networkCountryIso.isNotBlank()) {
            CurrencyCode.regionsCurrencies[RegionCode.tryValueOf(networkCountryIso)]
        } else null

        val simCountryIsoCurrency = if (simCountryIso.isNotBlank()) {
            CurrencyCode.regionsCurrencies[RegionCode.tryValueOf(simCountryIso)]
        } else null

        val localeIsoCurrency =
            runCatching { Currency.getInstance(localCountry).currencyCode }.getOrNull()
                ?.let { CurrencyCode.tryValueOf(it) }

        val defaultCurrency = simCountryIsoCurrency ?: localeIsoCurrency ?: networkCountryIsoCurrency

        return defaultCurrency?.name ?: CurrencyCode.USD.name
    }
}