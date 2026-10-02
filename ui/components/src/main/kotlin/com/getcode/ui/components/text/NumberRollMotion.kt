package com.getcode.ui.components.text

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Splits a readout into role-keyed characters: `P0..` prefix, `L0..` integer characters counted from
 * the point, `dot`, `R0..` fraction digits, `S0..` suffix. Keying by role rather than position keeps
 * each digit in its own slot when the string gets longer or shorter (`$170K` -> `$27.0K`).
 */
internal fun numberSlots(text: String): Map<String, Char> {
    val out = LinkedHashMap<String, Char>()
    val first = text.indexOfFirst { it.isDigit() }
    if (first < 0) {
        text.forEachIndexed { i, c -> out["P$i"] = c }
        return out
    }
    val last = text.indexOfLast { it.isDigit() }
    for (i in 0 until first) out["P$i"] = text[i]
    for (i in last + 1 until text.length) out["S${i - last - 1}"] = text[i]
    val middle = text.substring(first, last + 1)
    val dot = middle.indexOf('.')
    val left = if (dot >= 0) middle.substring(0, dot) else middle
    left.forEachIndexed { i, c -> out["L${left.length - 1 - i}"] = c }
    if (dot >= 0) {
        out["dot"] = '.'
        for (j in dot + 1 until middle.length) out["R${j - dot - 1}"] = middle[j]
    }
    return out
}

/** The pure result of [glyphMotion]: [travel] in glyph heights, [alpha], and [blur] as a 0..1 fraction of the full radius. */
internal data class GlyphMotion(val travel: Float, val alpha: Float, val blur: Float)

/**
 * numericText look: a glyph barely leaves the baseline (at most [MaxTravel] of its height) and
 * mid-transition both glyphs are heavy blurred smudges. [d] is the normalised offset (-1..1), [fade]
 * the glyph's own opacity ramp, [p] the transition progress that drives the blur.
 */
internal fun glyphMotion(d: Float, fade: Float, p: Float, presence: Float): GlyphMotion {
    // A retarget restarts p at 0 with the glyph already displaced, so blur follows the larger of the
    // transition arc and the glyph's own distance from rest; only a settled glyph is sharp.
    val arc = sin(PI * p.coerceIn(0f, 1f)).toFloat()
    return GlyphMotion(
        travel = d * MaxTravel,
        alpha = fade * presence,
        blur = maxOf(arc, abs(d).coerceIn(0f, 1f)),
    )
}

/** The outgoing glyph is fully faded by p = [OutgoingFadeEnd]. */
internal fun outgoingFade(p: Float): Float = 1f - (p / OutgoingFadeEnd).coerceIn(0f, 1f)

internal const val OutgoingFadeEnd = 0.6f

/** The incoming glyph starts appearing at p = 0.4. */
internal fun incomingFade(p: Float): Float = ((p - 0.4f) / 0.6f).coerceIn(0f, 1f)

internal const val MaxTravel = 0.3f

/**
 * The number a formatted readout stands for, enough to tell which way it moved: `$22.7K` is 22,700
 * and `-$41.67` is below `-$44.02`.
 */
internal fun numericValue(text: String): Double {
    val number = text.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: return 0.0
    val scale = when {
        text.endsWith("K") -> 1e3
        text.endsWith("M") -> 1e6
        text.endsWith("B") -> 1e9
        text.endsWith("T") -> 1e12
        else -> 1.0
    }
    val sign = if (text.trimStart().let { it.startsWith("-") || it.startsWith("−") }) -1.0 else 1.0
    return sign * number * scale
}
