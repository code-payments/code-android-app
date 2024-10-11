package com.getcode.utils

import com.getcode.model.Kin
import java.text.NumberFormat
import java.util.*
import kotlin.math.floor
import kotlin.math.roundToInt


object FormatUtils {
    fun round(value: Double): Double = (value * 100.0).roundToInt() / 100.0
    private fun roundCurrency(value: Double): Double = floor(value * 100) / 100.0
    fun getFiatValue(kinBalance: Double, rate: Double): Double = roundCurrency(kinBalance.toInt() / (1.0 / rate))
    fun getKinValue(amount: Double, rate: Double): Kin {
        if (rate == 0.0) return Kin(0)
        return Kin.fromKin(amount * (1 / rate))
    }

    fun format(value: Double): String = String.format("%,.2f", round(value))
    fun formatWholeRoundDown(value: Double): String = String.format("%,.0f", floor(value))
    fun formatCurrency(value: Double, locale: Locale): String =
        NumberFormat.getCurrencyInstance(locale).format(value)

    fun formatCurrency(value: Double, currencyCode: com.getcode.model.CurrencyCode): String {
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
