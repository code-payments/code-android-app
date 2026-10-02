package com.flipcash.app.tokens.internal.explainer

import com.getcode.opencode.internal.extensions.fractionDigits
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Rate
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.max

/**
 * Shows the explainer's dollar figures in the user's preferred currency. The curve math stays in USD;
 * only display is converted, with the same [Rate] the token screen uses for its balance and appreciation.
 */
internal class ExplainerCurrency(private val rate: Rate) {

    /** [usd] converted to the preferred currency. */
    fun convert(usd: BigDecimal): Fiat = Fiat(fiat = usd.toDouble() * rate.fx, currencyCode = rate.currency)

    /** A USD [Fiat] converted to the preferred currency. */
    fun convert(usd: Fiat): Fiat = usd.convertingTo(rate)

    /** `$5K`, `$22.7K`, `$1M`: a USD reserve as a compact label in the preferred currency. */
    fun reserve(usd: BigDecimal): String {
        var value = convert(usd).decimalValue
        var unit = 0
        while (unit < UNITS.lastIndex && trim(value).abs() >= THOUSAND) {
            value /= 1_000.0
            unit++
        }
        return symbol() + trim(value).toPlainString() + UNITS[unit]
    }

    /** A USD per-token price in the preferred currency, to four significant figures: `$0.02994`, `$8.78`. */
    fun price(usd: BigDecimal): String {
        val converted = BigDecimal(usd.toDouble() * rate.fx)
        var rounded = converted.round(MathContext(4, RoundingMode.HALF_UP)).stripTrailingZeros()
        val minDigits = rate.currency.fractionDigits
        if (rounded.scale() < minDigits) rounded = rounded.setScale(minDigits)
        return Fiat(fiat = rounded.toDouble(), currencyCode = rate.currency)
            .formatted(rule = Fiat.FormattingRule.Length(rounded.scale()))
    }

    private fun symbol() = rate.currency.singleCharacterCurrencySymbol.orEmpty()

    private fun trim(v: Double): BigDecimal =
        BigDecimal(v).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros()

    private companion object {
        val UNITS = listOf("", "K", "M", "B", "T")
        val THOUSAND: BigDecimal = BigDecimal(1_000)
    }
}

internal object ExplainerFormat {
    private val MIN_SHOWN_PERCENT = BigDecimal("0.01")

    /** A share as a percentage to two decimals, or `<0.01%` for a positive share below that. */
    fun percent(value: BigDecimal): String {
        if (value.signum() == 0) return "0%"
        if (value.signum() > 0 && value < MIN_SHOWN_PERCENT) return "<0.01%"
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString() + "%"
    }
}

/** A tick label measured in pixels: its [centre] on the track and its text [width]. */
internal data class ExplainerLabelBox(val centre: Float, val width: Float, val isToday: Boolean)

/**
 * Decides which slider labels to draw and where. Today is always drawn; every other label is
 * clamped inside `[0, totalWidth]` and dropped when its bounds come within [minGap] of Today's or of
 * a label already drawn. Returns the left edge of each drawn label, or null for a dropped one.
 */
internal fun layoutExplainerLabels(labels: List<ExplainerLabelBox>, totalWidth: Float, minGap: Float): List<Float?> {
    val lefts = labels.map { (it.centre - it.width / 2f).coerceIn(0f, max(0f, totalWidth - it.width)) }
    val drawn = mutableListOf<Int>()
    val result = MutableList<Float?>(labels.size) { null }
    val order = labels.indices.sortedBy { if (labels[it].isToday) 0 else 1 }
    for (i in order) {
        val clashes = drawn.any { j ->
            lefts[i] < lefts[j] + labels[j].width + minGap && lefts[j] < lefts[i] + labels[i].width + minGap
        }
        if (labels[i].isToday || !clashes) {
            drawn += i
            result[i] = lefts[i]
        }
    }
    return result
}
