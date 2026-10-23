package com.flipcash.app.core.util

import com.getcode.opencode.utils.roundTo
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10

/**
 * This number capped at [maxDigits] digits: anything below the first scale is printed whole, and
 * larger values are scaled to K/M/B/T with only as many decimals as the cap leaves room for —
 * trailing zeros dropped. 1,234 reads "1.23K"; 693,000 reads "693K".
 */
fun Number.abbreviated(maxDigits: Int = 3): String {
    val value = toDouble()
    val abbreviation = value.abbreviatedIn(maxDigits)
        ?: return value.roundTo(0).toLong().toString()

    return "%.${abbreviation.decimals}f".format(abbreviation.value) + abbreviation.suffix
}

/** An amount rewritten into [suffix]'s scale, to be shown with [decimals] decimal places. */
internal data class Abbreviation(
    val value: Double,
    val decimals: Int,
    val suffix: String,
)

/**
 * This value rounded to [maxDigits] significant digits and expressed in the largest scale it
 * clears, or null when it sits below the first scale and should be shown as it is.
 *
 * The rounding happens before the scale is picked, so a value that carries into the next one
 * (999,999 → 1M) is printed in the scale it lands in rather than as "1,000K".
 */
internal fun Double.abbreviatedIn(maxDigits: Int): Abbreviation? {
    if (this == 0.0) return null

    val exponent = floor(log10(abs(this))).toInt()
    val rounded = roundTo(maxDigits - 1 - exponent)

    val (scale, suffix) = SCALES.lastOrNull { (scale, _) -> abs(rounded) >= scale } ?: return null

    val value = rounded / scale
    val wholeDigits = abs(value).toLong().toString().length
    return Abbreviation(
        value = value,
        decimals = minimalDecimals(value, max = (maxDigits - wholeDigits).coerceAtLeast(0)),
        suffix = suffix,
    )
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
