package com.flipcash.app.tokens.internal.explainer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A readout whose digits roll like SwiftUI's `.contentTransition(.numericText())`.
 *
 * Characters are slots keyed by role (integer digit from the point, fraction digit, suffix), so
 * a longer or shorter string keeps each digit in its own slot. Each digit slot is an odometer: its position is an [Animatable] that is retargeted (keeping
 * its velocity) whenever the digit changes, and it draws the current and the next digit offset by
 * the fractional position with a fade. During a continuous drag the digits therefore glide instead
 * of restarting a transition on every value. Digit slots share the widest digit's width; symbols and
 * suffixes crossfade only when they change; a slot appearing or disappearing animates its width.
 *
 * Local to the explainer so [com.getcode.ui.components.text.AnimatedNumberText] keeps its behaviour
 * for the other screens that use it.
 */
@Composable
internal fun ExplainerNumberText(
    value: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    val measurer = rememberTextMeasurer()
    val digitWidth = remember(style) {
        ('0'..'9').maxOf { measurer.measure(it.toString(), style).size.width }
    }

    val holder = remember { DirectionHolder(value) }
    if (holder.last != value) {
        holder.rising = numericValue(value) >= numericValue(holder.last)
        holder.last = value
    }
    val rising = holder.rising

    // Slots are keyed by role (prefix, integer digit from the point, decimal, fraction digit, suffix)
    // rather than by position in the string, so $170K -> $27.0K keeps each digit in its own slot. The
    // largest extent seen is kept so a slot that empties can animate out instead of vanishing.
    val layout = remember { SlotLayout() }
    val chars = numberSlots(value)
    layout.absorb(chars)

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        for (slotKey in layout.keys()) {
            key(slotKey) {
                NumberSlot(
                    char = chars[slotKey],
                    rising = rising,
                    digitWidthPx = digitWidth,
                    style = style,
                    color = color,
                )
            }
        }
    }
}

/** Splits a readout into role-keyed characters: `P0..` prefix, `L0..` integer digits from the point, `dot`, `R0..` fraction, `S0..` suffix. */
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

private class SlotLayout {
    private var prefix = 0
    private var left = 0
    private var right = 0
    private var suffix = 0
    private var dot = false

    fun absorb(chars: Map<String, Char>) {
        for (k in chars.keys) {
            val n = k.drop(1).toIntOrNull()?.plus(1) ?: 0
            when {
                k == "dot" -> dot = true
                k[0] == 'P' -> prefix = maxOf(prefix, n)
                k[0] == 'L' -> left = maxOf(left, n)
                k[0] == 'R' -> right = maxOf(right, n)
                k[0] == 'S' -> suffix = maxOf(suffix, n)
            }
        }
    }

    fun keys(): List<String> = buildList {
        for (i in 0 until prefix) add("P$i")
        for (i in left - 1 downTo 0) add("L$i")
        if (dot) add("dot")
        for (i in 0 until right) add("R$i")
        for (i in 0 until suffix) add("S$i")
    }
}

@Composable
private fun NumberSlot(
    char: Char?,
    rising: Boolean,
    digitWidthPx: Int,
    style: TextStyle,
    color: Color,
) {
    // Keep the last real character so a slot that empties can fade and shrink out.
    val shown = remember { CharHolder(char ?: ' ') }
    if (char != null) shown.value = char
    val shownChar = shown.value
    val isDigit = shownChar.isDigit()

    val presence = remember { Animatable(if (char != null) 1f else 0f) }
    LaunchedEffect(char != null) {
        presence.animateTo(if (char != null) 1f else 0f, tween(220))
    }

    // Two-glyph transition like numericText: the old glyph slides out and the new one slides in from
    // the other side, with nothing drawn for the digits in between. Offsets are in glyph heights:
    // out = s + (-dir - s) * p, in = dir * (1 - p). A retarget keeps the glyph that is showing most as
    // the new outgoing one, starting from its current offset and velocity.
    val initial = if (shownChar.isDigit()) shownChar.digitToInt() else 0
    val outDigit = remember { mutableIntStateOf(initial) }
    val inDigit = remember { mutableIntStateOf(initial) }
    val startOffset = remember { mutableFloatStateOf(0f) }
    val dir = remember { mutableFloatStateOf(1f) }
    val progress = remember { Animatable(1f) }
    val track = remember { OdometerTrack(if (shownChar.isDigit()) shownChar.digitToInt() else -1, 0f) }
    LaunchedEffect(char) {
        if (char == null || !char.isDigit()) {
            track.digit = -1
            return@LaunchedEffect
        }
        val next = char.digitToInt()
        if (track.digit < 0) {
            outDigit.intValue = next
            inDigit.intValue = next
            progress.snapTo(1f)
        } else if (next != inDigit.intValue) {
            val p = progress.value
            val vp = progress.velocity
            val dOut = startOffset.floatValue + (-dir.floatValue - startOffset.floatValue) * p
            val dIn = dir.floatValue * (1f - p)
            val inDominant = abs(dIn) <= abs(dOut)
            val d0 = if (inDominant) dIn else dOut
            val vDisp = if (inDominant) -dir.floatValue * vp else vp * (-dir.floatValue - startOffset.floatValue)
            outDigit.intValue = if (inDominant) inDigit.intValue else outDigit.intValue
            inDigit.intValue = next
            startOffset.floatValue = d0
            dir.floatValue = if (rising) 1f else -1f
            val denom = -dir.floatValue - d0
            val v0 = if (abs(denom) > 0.3f) vDisp / denom else 0f
            progress.snapTo(0f)
            progress.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 380f),
                initialVelocity = v0,
            )
        }
        track.digit = next
    }

    val fade = remember { Animatable(1f) }
    LaunchedEffect(char) {
        if (char != null && !char.isDigit() && track.symbol != char) {
            if (track.symbol != null) {
                fade.snapTo(0f)
                fade.animateTo(1f, tween(180))
            }
        }
        track.symbol = if (char != null && !char.isDigit()) char else null
    }

    Box(
        modifier = Modifier.layout { measurable, _ ->
            val p = measurable.measure(Constraints())
            val w = if (isDigit) digitWidthPx else p.width
            layout((w * presence.value).roundToInt(), p.height) {
                p.place(((w * presence.value).roundToInt() - p.width) / 2, 0)
            }
        }.drawWithContent {
            clipRect(left = 0f, top = 0f, right = size.width, bottom = size.height) {
                this@drawWithContent.drawContent()
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        if (isDigit) {
            val glyphWidth = with(LocalDensity.current) { digitWidthPx.toDp() }
            // Outgoing glyph.
            Text(
                modifier = Modifier
                    .width(glyphWidth)
                    .graphicsLayer {
                        val p = progress.value
                        val d = startOffset.floatValue + (-dir.floatValue - startOffset.floatValue) * p
                        applyGlyphMotion(d, 1f - (p / 0.6f).coerceIn(0f, 1f), p, presence.value)
                    },
                text = outDigit.intValue.toString(), style = style, color = color,
                maxLines = 1, softWrap = false, textAlign = TextAlign.Center,
            )
            // Incoming glyph.
            Text(
                modifier = Modifier
                    .width(glyphWidth)
                    .graphicsLayer {
                        val p = progress.value
                        val d = dir.floatValue * (1f - p)
                        applyGlyphMotion(d, ((p - 0.4f) / 0.6f).coerceIn(0f, 1f), p, presence.value)
                    },
                text = inDigit.intValue.toString(), style = style, color = color,
                maxLines = 1, softWrap = false, textAlign = TextAlign.Center,
            )
        } else {
            Text(
                modifier = Modifier.graphicsLayer { alpha = fade.value * presence.value },
                text = shownChar.toString(), style = style, color = color,
                maxLines = 1, softWrap = false,
            )
        }
    }
}

/**
 * numericText look: the glyph barely leaves the baseline (at most [MaxTravel] of its height), and
 * mid-transition both glyphs are heavy blurred smudges. [d] is the normalised offset (-1..1), [fade]
 * the glyph's own opacity ramp and [p] the transition progress that drives the blur. Below API 31
 * `RenderEffect` is ignored, leaving the short travel and the alpha crossfade.
 */
private fun androidx.compose.ui.graphics.GraphicsLayerScope.applyGlyphMotion(d: Float, fade: Float, p: Float, presence: Float) {
    translationY = d * MaxTravel * size.height
    alpha = fade * presence
    // A retarget restarts p at 0 with the glyph already displaced, so blur follows the larger of the
    // transition arc and the glyph's own distance from rest; only a settled glyph is sharp.
    val arc = kotlin.math.sin(Math.PI * p.coerceIn(0f, 1f)).toFloat()
    val radius = 11.dp.toPx() * maxOf(arc, abs(d).coerceIn(0f, 1f))
    renderEffect = if (radius > 0.5f) {
        androidx.compose.ui.graphics.BlurEffect(radius, radius, androidx.compose.ui.graphics.TileMode.Decal)
    } else {
        null
    }
}

private const val MaxTravel = 0.3f

private class CharHolder(var value: Char)

private class OdometerTrack(var digit: Int, var logical: Float, var symbol: Char? = null)

private class DirectionHolder(var last: String, var rising: Boolean = true)


/** The number a formatted readout stands for, enough to tell whether it went up: `$22.7K` is 22,700. */
internal fun numericValue(text: String): Double {
    val number = text.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: return 0.0
    val scale = when {
        text.endsWith("K") -> 1e3
        text.endsWith("M") -> 1e6
        text.endsWith("B") -> 1e9
        text.endsWith("T") -> 1e12
        else -> 1.0
    }
    return number * scale
}
