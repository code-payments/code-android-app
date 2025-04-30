package com.getcode.opencode.internal.extensions

import android.icu.util.Currency
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.utils.trace
import java.util.Locale

internal fun CurrencyCode.getClosestLocale(): Locale {
    val currency = Currency.getInstance(name.uppercase())

    val locales = Locale.getAvailableLocales()
    var matchedLocale: Locale? = null

    val customLanguageMap = mapOf(
        "csw" to "en", // Map Swampy Cree (csw) to English for locale purposes
    )

    for (locale in locales) {
        if (locale.country.equals(getRegion()?.name, ignoreCase = true)) {
            try {
                val localeCurrency = Currency.getInstance(locale)
                if (localeCurrency.currencyCode.lowercase() == currency.currencyCode.lowercase()) {
                    matchedLocale = locale
                    break
                }
            } catch (e: IllegalArgumentException) {
                // Some locales may not have a currency; skip them
                continue
            }
        }
    }

    println("Matched locale: $matchedLocale")

    val language = customLanguageMap[matchedLocale?.language] ?: matchedLocale?.language
    val country = matchedLocale?.country
    return try {
        Locale("${language}_${country}")
    } catch (e: IllegalArgumentException) {
        trace("Failed to get locale for $name")
        locale()
    }
}

private fun CurrencyCode.locale() = Locale(Locale.getDefault().language, getRegion()?.name.orEmpty())