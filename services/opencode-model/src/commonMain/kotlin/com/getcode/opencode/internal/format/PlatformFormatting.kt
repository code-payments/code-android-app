package com.getcode.opencode.internal.format

import com.flipcash.libs.currency.math.decimal.BigDecimal
import com.getcode.opencode.model.financial.CurrencyCode

// The locale-dependent number formatting and parsing this module does. On the JVM targets each
// actual is the DecimalFormat code that used to sit at its call site, unchanged; on JS it goes
// through Intl. A null [localeTag] means the platform's default locale.

/** [com.getcode.opencode.model.financial.Fiat.formatted]: exactly [fractionDigits], HALF_UP; a negative value prints `-` before [prefix]. */
internal expect fun formatFiat(
    value: Double,
    fractionDigits: Int,
    prefix: String,
    suffix: String,
    grouping: Boolean,
    localeTag: String? = null,
): String

/** en-US grouping, HALF_UP; [fractionDigits] null keeps the platform's default (0 to 3 digits). */
internal expect fun formatUsDecimal(value: Double, fractionDigits: Int?): String

/** en-US grouping, HALF_UP, 0 to [maxFractionDigits] digits, from the exact decimal. */
internal expect fun formatUsQuantity(value: BigDecimal, maxFractionDigits: Int): String

/** Parses the leading en-US number of [text] (grouping allowed); throws when there is none. */
internal expect fun parseUsDecimal(text: String): Double?

/** Parses the leading number of [text] in the locale, without grouping; null when there is none. */
internal expect fun parseLocaleDecimal(text: String, decimalPlaces: Int, localeTag: String? = null): Double?

/** The locale's decimal separator. */
internal expect fun decimalSeparator(localeTag: String? = null): Char

/** `java.util.Currency.getDefaultFractionDigits` for [code]; throws IllegalArgumentException for a code the JDK doesn't know. */
internal expect fun isoFractionDigits(code: String): Int

/** Every symbol each currency is written with. Built once; currencies without one are left out. */
internal expect fun buildCurrencySymbolTable(): Map<CurrencyCode, Set<String>>
