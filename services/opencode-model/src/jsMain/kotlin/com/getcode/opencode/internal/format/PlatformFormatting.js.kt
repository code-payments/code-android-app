package com.getcode.opencode.internal.format

import com.flipcash.libs.currency.math.decimal.BigDecimal

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

internal actual fun decimalSeparator(localeTag: String?): Char {
    val parts = numberFormat(localeTag, options { }).formatToParts(1.5)
    val length = parts.length as Int
    for (i in 0 until length) {
        if (parts[i].type == "decimal") return (parts[i].value as String)[0]
    }
    return '.'
}
