package com.flipcash.app.core.money

import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
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

fun LocalFiat.formatted(formatting: Fiat.FormattingRule = Fiat.FormattingRule.None): String {
    return nativeAmount.formatted(rule = formatting)
}

/**
 * Formats a fiat appreciation/depreciation with the shared sign convention used across the balance
 * header ([com.flipcash.app.core.ui.CurrencyAppreciationLabel]) and the per-token cards: a leading
 * "+" for a gain or a zero change (always "+$0.00"), and the formatter's own "-" for a loss.
 *
 * A change that rounds to zero is normalized to a positive zero before formatting — otherwise a
 * tiny-negative amount would render as "-$0.00" (the formatter uses the raw value while
 * [Fiat.valueNonZero]/[Fiat.toDouble] round to the currency's precision).
 */
fun Fiat.formattedAppreciation(): String {
    val isZero = !valueNonZero()
    val hasAppreciation = toDouble() >= 0
    return when {
        isZero -> copy(quarks = 0).formatted(extraPrefix = "+")
        hasAppreciation -> formatted(extraPrefix = "+")
        else -> formatted()
    }
}
