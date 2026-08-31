package com.flipcash.app.core.util

import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.utils.roundTo
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10

/**
 * The amount formatted to fit a fixed-width control, capped at [maxDigits] digits: anything under
 * the first scale is formatted as usual, and larger amounts are scaled to K/M/B/T with only as many
 * decimals as the cap leaves room for — trailing zeros dropped.
 *
 * The cap is what keeps a localized amount inside its button: a $20 tip stays "$20", but the same
 * tip in rupiah is Rp332,000, which shows as "Rp332K" rather than overflowing.
 */
fun Fiat.abbreviated(maxDigits: Int = 3): String {
    if (decimalValue == 0.0) return formatted(rule = Fiat.FormattingRule.Truncated)

    // Round to [maxDigits] significant digits before picking the scale, so a value that carries
    // into the next one (999,999 → 1M) is scaled by the one it lands in, not printed as "1,000K".
    val exponent = floor(log10(abs(decimalValue))).toInt()
    val value = decimalValue.roundTo(maxDigits - 1 - exponent)

    val (scale, suffix) = SCALES.lastOrNull { (scale, _) -> abs(value) >= scale }
        ?: return formatted(rule = Fiat.FormattingRule.Truncated)

    val scaled = value / scale
    val wholeDigits = abs(scaled).toLong().toString().length
    val decimals = minimalDecimals(scaled, max = (maxDigits - wholeDigits).coerceAtLeast(0))

    return Fiat(fiat = scaled, currencyCode = currencyCode)
        .formatted(rule = Fiat.FormattingRule.Length(decimals)) + suffix
}

/**
 * The fewest decimal places that show [value] as precisely as [max] would — so a scaled 1.50 keeps
 * one place ("1.5") and 2.00 none ("2"), instead of being padded out to the cap.
 */
private fun minimalDecimals(value: Double, max: Int): Int {
    var decimals = max
    while (decimals > 0 && value.roundTo(decimals) == value.roundTo(decimals - 1)) {
        decimals--
    }
    return decimals
}

/** Scales in ascending order; the largest one the amount clears is the one it's printed in. */
private val SCALES = listOf(
    1_000.0 to "K",
    1_000_000.0 to "M",
    1_000_000_000.0 to "B",
    1_000_000_000_000.0 to "T",
)
