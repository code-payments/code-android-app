package com.getcode.util.locale

import android.content.Context
import android.telephony.TelephonyManager
import com.getcode.model.CurrencyCode
import com.getcode.model.CurrencyCode.Companion
import com.getcode.model.RegionCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.*
import java.util.concurrent.TimeUnit

object LocaleUtils {

    fun getLanguageTag(): String = Locale.getDefault().toLanguageTag()

    suspend fun getDefaultCountry(context: Context): String? {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val networkCountryIso = tm.networkCountryIso.uppercase()
        val simCountryIso = tm.simCountryIso.uppercase()
        val localeCountry = context.resources.configuration.locale.country.uppercase()

        val simCountry = if (simCountryIso.isNotBlank() && simCountryIso.length == 2) simCountryIso else null
        val localeCountryValid = if (localeCountry.isNotBlank() && localeCountry.length == 2) localeCountry else null
        val networkCountry = if (networkCountryIso.isNotBlank() && networkCountryIso.length == 2) networkCountryIso else null

        return simCountry ?: localeCountryValid ?: networkCountry ?:getCountryFromIp()
    }

    private suspend fun getCountryFromIp(): String? = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder().url("http://ip-api.com/json/?fields=countryCode").build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body.string())
                val ipCountry = json.optString("countryCode", "").uppercase()
                if (ipCountry.length == 2) ipCountry else "US"
            } else null
        } catch (e: Exception) {
            return@withContext null
        }
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