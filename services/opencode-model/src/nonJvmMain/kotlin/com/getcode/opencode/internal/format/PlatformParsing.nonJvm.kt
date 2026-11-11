package com.getcode.opencode.internal.format

import com.getcode.opencode.model.financial.CurrencyCode

// Node and Apple parse with the port of Android 16's ICU parser and read decimal places and currency
// symbols from tables captured on an Android 16 device, so both match the device by construction.
// Neither Intl nor NSNumberFormatter can list its locales, and both use their own ICU and CLDR versions.

internal actual fun parseUsDecimal(text: String): Double? =
    icuParseDecimal(text, IcuSymbols.US, groupingUsed = true)
        ?: throw IllegalArgumentException("Unparseable number: \"$text\"")

internal actual fun parseLocaleDecimal(text: String, decimalPlaces: Int, localeTag: String?): Double? =
    icuParseDecimal(text, IcuSymbols.forLocale(localeTag), groupingUsed = false)

internal actual fun isoFractionDigits(code: String): Int = AndroidFractionDigits.of(code)

internal actual fun buildCurrencySymbolTable(): Map<CurrencyCode, Set<String>> =
    CurrencyCode.entries.mapNotNull { code -> AndroidCurrencySymbols.table[code.name]?.let { code to it.toSet() } }.toMap()
