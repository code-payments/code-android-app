package com.getcode.opencode.internal.format

import com.flipcash.libs.currency.math.decimal.BigDecimal
import com.getcode.opencode.model.financial.CurrencyCode

// Intl.NumberFormat rounds half away from zero (halfExpand), which is HALF_UP. Unlike DecimalFormat,
// it rounds the shortest decimal of a Double rather than its exact binary value, so a tie such as
// 1.005 can print 1.01 here where the JVM prints 1.00.

private fun numberFormat(localeTag: String?, options: dynamic): dynamic {
    val intl = js("Intl")
    val locale: dynamic = localeTag ?: undefined
    return js("new intl.NumberFormat(locale, options)")
}

private fun options(build: dynamic.() -> Unit): dynamic {
    val o: dynamic = js("({})")
    o.build()
    return o
}

private fun isNegative(value: Double): Boolean = value < 0.0 || (value == 0.0 && 1.0 / value < 0.0)

internal actual fun formatFiat(
    value: Double,
    fractionDigits: Int,
    prefix: String,
    suffix: String,
    grouping: Boolean,
    localeTag: String?,
): String {
    val format = numberFormat(localeTag, options {
        minimumFractionDigits = fractionDigits
        maximumFractionDigits = fractionDigits
        useGrouping = grouping
    })
    // DecimalFormat keeps the sign of a negative that rounds to zero ("-$0.00"); so does this.
    val digits = format.format(kotlin.math.abs(value)) as String
    return (if (isNegative(value)) "-$prefix" else prefix) + digits + suffix
}

internal actual fun formatUsDecimal(value: Double, fractionDigits: Int?): String {
    val format = numberFormat("en-US", options {
        if (fractionDigits != null) {
            minimumFractionDigits = fractionDigits
            maximumFractionDigits = fractionDigits
        }
    })
    return format.format(value) as String
}

internal actual fun formatUsQuantity(value: BigDecimal, maxFractionDigits: Int): String {
    // A string argument is formatted as the exact decimal, so no digits are lost to a Double.
    val format = numberFormat("en-US", options {
        minimumFractionDigits = 0
        maximumFractionDigits = maxFractionDigits
    })
    return format.format(value.toPlainString()) as String
}

/** The leading `-?digits[.digits]` of [text], with [group] characters allowed in the integer part. */
private fun leadingNumber(text: String, decimal: Char, group: Char?): Double? {
    val out = StringBuilder()
    var i = 0
    if (i < text.length && text[i] == '-') { out.append('-'); i++ }
    var digits = 0
    while (i < text.length && (text[i].isAsciiDigit() || (group != null && text[i] == group))) {
        if (text[i] != group) { out.append(text[i]); digits++ }
        i++
    }
    if (i < text.length && text[i] == decimal) {
        var fraction = 0
        val mark = out.length
        out.append('.')
        i++
        while (i < text.length && text[i].isAsciiDigit()) { out.append(text[i]); fraction++; i++ }
        if (fraction == 0) out.setLength(mark)
        digits += fraction
    }
    if (digits == 0) return null
    return out.toString().toDouble()
}

private fun Char.isAsciiDigit() = this in '0'..'9'

internal actual fun parseUsDecimal(text: String): Double? =
    leadingNumber(text, decimal = '.', group = ',') ?: throw IllegalArgumentException("Unparseable number: \"$text\"")

internal actual fun parseLocaleDecimal(text: String, decimalPlaces: Int, localeTag: String?): Double? =
    leadingNumber(text, decimal = decimalSeparator(localeTag), group = null)

internal actual fun decimalSeparator(localeTag: String?): Char {
    val parts = numberFormat(localeTag, options { }).formatToParts(1.5)
    val length = parts.length as Int
    for (i in 0 until length) {
        if (parts[i].type == "decimal") return (parts[i].value as String)[0]
    }
    return '.'
}

internal actual fun isoFractionDigits(code: String): Int = JdkFractionDigits.of(code)

// Intl can't list its locales, and the symbols it gives disagree with the JVM's for 18 currencies,
// so this uses the JVM's table.
internal actual fun buildCurrencySymbolTable(): Map<CurrencyCode, Set<String>> =
    CurrencyCode.entries.mapNotNull { code -> JdkCurrencySymbols.table[code.name]?.let { code to it.toSet() } }.toMap()
