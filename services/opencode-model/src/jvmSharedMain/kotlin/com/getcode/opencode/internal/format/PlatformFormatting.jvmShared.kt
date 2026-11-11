package com.getcode.opencode.internal.format

import com.flipcash.libs.currency.math.decimal.BigDecimal
import com.getcode.opencode.internal.extensions.getClosestLocale
import com.getcode.opencode.model.financial.CurrencyCode
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

private fun localeOrDefault(localeTag: String?): Locale? = localeTag?.let(Locale::forLanguageTag)

internal actual fun formatFiat(
    value: Double,
    fractionDigits: Int,
    prefix: String,
    suffix: String,
    grouping: Boolean,
    localeTag: String?,
): String {
    val locale = localeOrDefault(localeTag)
    val formatter = (if (locale == null) DecimalFormat.getInstance() else DecimalFormat.getInstance(locale)).apply {
        minimumFractionDigits = fractionDigits
        maximumFractionDigits = fractionDigits
        roundingMode = RoundingMode.HALF_UP
        (this as DecimalFormat).decimalFormatSymbols =
            decimalFormatSymbols.apply {
                currencySymbol = ""
            }

        positivePrefix = prefix
        positiveSuffix = suffix
        negativePrefix = "-$prefix"
        negativeSuffix = suffix
        isGroupingUsed = grouping
    }

    return formatter.format(value)
}

internal actual fun formatUsDecimal(value: Double, fractionDigits: Int?): String {
    val formatter = DecimalFormat.getInstance(Locale.US).apply {
        if (fractionDigits != null) {
            maximumFractionDigits = fractionDigits
            minimumFractionDigits = fractionDigits
        }
        roundingMode = RoundingMode.HALF_UP
    }

    return formatter.format(value)
}

internal actual fun formatUsQuantity(value: BigDecimal, maxFractionDigits: Int): String {
    val formatter = DecimalFormat.getInstance(Locale.US).apply {
        maximumFractionDigits = maxFractionDigits
        minimumFractionDigits = 0
        roundingMode = RoundingMode.HALF_UP
    }
    return formatter.format(value)
}

internal actual fun parseUsDecimal(text: String): Double? {
    // Fraction digits don't affect DecimalFormat.parse; they were set here before, so keep them.
    val formatter = DecimalFormat.getNumberInstance(Locale.US).apply {
        isParseIntegerOnly = false
        minimumFractionDigits = 6
        maximumFractionDigits = 6
    }
    return formatter.parse(text)?.toDouble()
}

internal actual fun parseLocaleDecimal(text: String, decimalPlaces: Int, localeTag: String?): Double? {
    val locale = localeOrDefault(localeTag)
    return runCatching {
        (if (locale == null) NumberFormat.getInstance() else NumberFormat.getInstance(locale)).apply {
            isGroupingUsed = false
            isParseIntegerOnly = false
            maximumFractionDigits = decimalPlaces
            minimumFractionDigits = decimalPlaces
        }.parse(text)?.toDouble()
    }.getOrNull()
}

internal actual fun decimalSeparator(localeTag: String?): Char {
    val locale = localeOrDefault(localeTag)
    return (if (locale == null) DecimalFormatSymbols.getInstance() else DecimalFormatSymbols.getInstance(locale)).decimalSeparator
}

internal actual fun isoFractionDigits(code: String): Int = Currency.getInstance(code).defaultFractionDigits

internal actual fun buildCurrencySymbolTable(): Map<CurrencyCode, Set<String>> = buildMap {
    CurrencyCode.entries.forEach { currency ->
        try {
            val locale = currency.getClosestLocale()
            val currencyInstance = Currency.getInstance(currency.name)
            val usSymbol = currencyInstance.getSymbol(Locale.US)
            val localeSymbol = currencyInstance.getSymbol(locale)
            // The closest locale can be one that still prefixes the country (CA$
            // under csw_CA), so take the symbol every locale of the region uses for it.
            val region = currency.getRegion()?.name
            val regionSymbols = Locale.getAvailableLocales()
                .filter { region != null && it.country.equals(region, ignoreCase = true) }
                .map { currencyInstance.getSymbol(it) }

            put(currency, setOf(usSymbol, localeSymbol) + regionSymbols)
        } catch (e: IllegalArgumentException) {
            // Skip currencies with no valid symbol
        }
    }
}
