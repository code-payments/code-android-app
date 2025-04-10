package com.flipcash.app.core.money

import com.getcode.opencode.model.core.CurrencyCode
import com.getcode.opencode.model.core.LocalFiat
import java.text.NumberFormat
import java.util.*
import kotlin.math.floor
import kotlin.math.roundToInt


object FormatUtils {
    fun round(value: Double): Double = (value * 100.0).roundToInt() / 100.0
    private fun roundCurrency(value: Double): Double = floor(value * 100) / 100.0
    fun localized(value: Double, rate: Double): Double = roundCurrency(value.toInt() / (1.0 / rate))

    fun format(value: Double): String = String.format("%,.2f", round(value))
    fun formatWholeRoundDown(value: Double): String = String.format("%,.0f", floor(value))
    fun formatCurrency(value: Double, locale: Locale): String =
        NumberFormat.getCurrencyInstance(locale).format(value)

    fun formatCurrency(value: Double, currencyCode: CurrencyCode): String {
        val locale = NumberFormat.getAvailableLocales().firstOrNull {
            NumberFormat.getCurrencyInstance(it).currency?.currencyCode == currencyCode.name
        } ?: Locale.getDefault()

        return formatCurrency(value, locale)
    }

    fun formatCurrency(value: Double, currencyCode: String): String {
        val locale = NumberFormat.getAvailableLocales().firstOrNull {
            NumberFormat.getCurrencyInstance(it).currency?.currencyCode == currencyCode
        } ?: Locale.getDefault()

        return formatCurrency(value, locale)
    }
}

fun Int.withCommas(): String {
    return this.toString().reversed().chunked(3).joinToString(",").reversed()
}

val LocalFiat.formatted: String
    get() {
        return converted.convertingTo(rate).formatted()
    }
