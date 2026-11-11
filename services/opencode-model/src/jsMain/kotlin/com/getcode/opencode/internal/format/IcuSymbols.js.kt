package com.getcode.opencode.internal.format

internal actual fun platformIcuSymbols(localeTag: String?): IcuSymbols {
    val intl = js("Intl")
    val locale: dynamic = localeTag ?: undefined
    fun parts(value: Double, options: dynamic): dynamic {
        val format = js("new intl.NumberFormat(locale, options)")
        return format.formatToParts(value)
    }
    fun find(parts: dynamic, type: String): String? {
        val length = parts.length as Int
        for (i in 0 until length) {
            if (parts[i].type == type) return parts[i].value as String
        }
        return null
    }

    val plain = js("({})")
    val scientific = js("({notation: 'scientific'})")
    val number = parts(-1234567.5, plain)
    val us = IcuSymbols.US
    return IcuSymbols(
        decimal = find(number, "decimal") ?: us.decimal,
        grouping = find(number, "group") ?: us.grouping,
        minus = find(number, "minusSign") ?: us.minus,
        exponent = find(parts(1e5, scientific), "exponentSeparator") ?: us.exponent,
        nan = find(parts(Double.NaN, plain), "nan") ?: us.nan,
        infinity = find(parts(Double.POSITIVE_INFINITY, plain), "infinity") ?: us.infinity,
    )
}

internal actual fun isNdSupplementary(cp: Int): Boolean {
    val c: dynamic = js("String").fromCodePoint(cp)
    return js("/^\\p{Nd}\$/u").test(c) as Boolean
}
