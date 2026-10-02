package com.getcode.ui.components.text

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FloatSpringSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.getcode.ui.utils.AutoSizeTextMeasurer
import com.getcode.ui.utils.ConstraintMode
import com.getcode.ui.utils.MeasureWidthFraction
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * A number whose digits roll like SwiftUI's `.contentTransition(.numericText())`.
 *
 * Characters are slots keyed by role (see [numberSlots]), so a longer or shorter string keeps each
 * digit in its own slot. A digit slot's transition is a spring stepped every frame and retargeted,
 * keeping its offset and velocity, whenever the digit changes again mid-roll, so a value that moves
 * every frame glides instead of restarting. Digits share the widest digit's width; symbols and
 * suffixes stay still unless they change; a slot appearing or disappearing animates its width.
 */
@Composable
fun AnimatedNumberText(
    value: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    constraintMode: ConstraintMode = ConstraintMode.Free,
) {
    BoxWithConstraints(modifier = modifier) {
        val textMeasurer = rememberTextMeasurer()
        val textSize = if (constraintMode is ConstraintMode.AutoSize) {
            val autosizeTextMeasurer = remember(textMeasurer) {
                AutoSizeTextMeasurer(textMeasurer)
            }
            val maxWidthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
            // Digits are drawn at the widest digit's width, so size the string as it will be drawn.
            val widest = remember(style) { textMeasurer.digitMetrics(style).widest }

            remember(value, style.fontSize, maxWidthPx, widest) {
                autosizeTextMeasurer.findFontSize(
                    text = AnnotatedString(value.map { if (it.isDigit()) widest else it }.joinToString("")),
                    style = style,
                    constraints = Constraints(
                        maxWidth = (maxWidthPx * MeasureWidthFraction).roundToInt(),
                        minHeight = 0
                    ),
                    minFontSize = constraintMode.minimum.fontSize,
                    maxFontSize = style.fontSize,
                    autosizeGranularity = 100
                )
            }
        } else {
            style.fontSize
        }

        val resolvedStyle = style.copy(fontSize = textSize)
        val digitWidthPx = remember(resolvedStyle) { textMeasurer.digitMetrics(resolvedStyle).widthPx }

        DeferredNumberRoll(
            value = value,
            style = resolvedStyle,
            color = color,
            digitWidthPx = digitWidthPx,
        )
    }
}

/**
 * Shows [value] as plain text until it first changes, then mounts the roller.
 *
 * The roller costs several times the `Text` it wraps to compose, and a screen that shows many
 * numbers at once -- the wallet's card deck -- would pay that for every character of every number
 * before the first frame, to animate nothing. A number that never moves costs a [Text] per character.
 *
 * The first change still rolls: the roller mounts seeded with the value already on screen, and only
 * follows [value] from the next frame, so it has something to animate to.
 */
@Composable
private fun DeferredNumberRoll(
    value: String,
    style: TextStyle,
    color: Color,
    digitWidthPx: Int,
) {
    // What the static row shows, and what the roller is seeded with when it mounts.
    val seed = remember { value }
    var mounted by remember { mutableStateOf(false) }
    var following by remember { mutableStateOf(false) }

    LaunchedEffect(value) {
        if (mounted) return@LaunchedEffect
        if (value != seed) mounted = true
    }
    // Restarts on the composition that mounted the roller.
    LaunchedEffect(mounted) {
        if (!mounted) return@LaunchedEffect
        // Give the roller a frame of its own before moving it. Effects run inside the frame that
        // composed them, so following [value] straight away would settle the mount and the move in
        // one pass and the digits would snap instead of rolling.
        withFrameNanos { }
        following = true
    }

    if (!mounted) {
        StaticNumber(value = seed, style = style, color = color, digitWidthPx = digitWidthPx)
    } else {
        RollingNumber(
            value = if (following) value else seed,
            style = style,
            color = color,
            digitWidthPx = digitWidthPx,
        )
    }
}

@Composable
private fun StaticNumber(
    value: String,
    style: TextStyle,
    color: Color,
    digitWidthPx: Int,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        value.forEach { char ->
            Text(
                modifier = if (char.isDigit()) Modifier.digitCell(digitWidthPx) else Modifier,
                text = char.toString(),
                style = style,
                color = color,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun RollingNumber(
    value: String,
    style: TextStyle,
    color: Color,
    digitWidthPx: Int,
) {
    val direction = remember { DirectionHolder(value) }
    if (direction.last != value) {
        direction.rising = numericValue(value) >= numericValue(direction.last)
        direction.last = value
    }

    // The largest extent seen is kept so a slot that empties can animate out instead of vanishing.
    val layout = remember { SlotLayout() }
    val chars = numberSlots(value)
    layout.absorb(chars)

    Row(verticalAlignment = Alignment.CenterVertically) {
        for ((index, slotKey) in layout.keys().withIndex()) {
            key(slotKey) {
                NumberSlot(
                    char = chars[slotKey],
                    staggerIndex = index,
                    rising = direction.rising,
                    digitWidthPx = digitWidthPx,
                    style = style,
                    color = color,
                )
            }
        }
    }
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
    staggerIndex: Int,
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
    val initial = if (isDigit) shownChar.digitToInt() else 0
    val outDigit = remember { mutableIntStateOf(initial) }
    val inDigit = remember { mutableIntStateOf(initial) }
    val startOffset = remember { mutableFloatStateOf(0f) }
    val dir = remember { mutableFloatStateOf(1f) }
    val motion = remember { RollMotion() }
    val track = remember { SlotTrack(if (isDigit) shownChar.digitToInt() else -1) }
    LaunchedEffect(char) {
        if (char == null || !char.isDigit()) {
            track.digit = -1
            return@LaunchedEffect
        }
        val next = char.digitToInt()
        if (track.digit < 0) {
            outDigit.intValue = next
            inDigit.intValue = next
            motion.settle()
        } else if (next != inDigit.intValue) {
            // numericText starts its characters left to right, about a frame apart, not all at once.
            // Only a settled slot waits: a slot already rolling (or already waiting) keeps its start
            // time, so a scrub that changes it every frame doesn't keep pushing the start back.
            if (!motion.active) motion.delayNanos = staggerIndex * StaggerNanos
            val p = motion.progress
            val vp = motion.velocity
            val dOut = startOffset.floatValue + (-dir.floatValue - startOffset.floatValue) * p
            val dIn = dir.floatValue * (1f - p)
            val inDominant = abs(dIn) <= abs(dOut)
            val newDir = if (rising) 1f else -1f
            if (motion.active && !inDominant && newDir == dir.floatValue) {
                // The outgoing glyph is still the one showing: let it keep leaving at its own pace
                // and swap in the newest digit. Restarting the roll from its offset would start it
                // near rest, so a fast scrub would hold the old digit in place.
                inDigit.intValue = next
                track.digit = next
                return@LaunchedEffect
            }
            val d0 = if (inDominant) dIn else dOut
            val vDisp = if (inDominant) -dir.floatValue * vp else vp * (-dir.floatValue - startOffset.floatValue)
            outDigit.intValue = if (inDominant) inDigit.intValue else outDigit.intValue
            inDigit.intValue = next
            startOffset.floatValue = d0
            dir.floatValue = newDir
            val denom = -dir.floatValue - d0
            motion.retarget(velocity = if (abs(denom) > 0.3f) vDisp / denom else 0f)
        }
        track.digit = next
    }
    // A change only retargets [motion]; this loop steps it every frame. Restarting an animation per
    // change instead would cost a frame and the velocity each time, so a scrub that changes the value
    // every frame would hold the old digit in place.
    LaunchedEffect(motion) {
        snapshotFlow { motion.kicks }.collect { motion.run(RollSpring) }
    }
    // The outgoing glyph is invisible from OutgoingFadeEnd on, so it leaves the tree there: a settled
    // digit is one node, and TalkBack doesn't read the number twice.
    val outgoingMounted by remember { derivedStateOf { motion.progress < OutgoingFadeEnd } }

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

    // A slot that has emptied and finished shrinking draws nothing, so it leaves nothing behind.
    if (char == null && !presence.isRunning && presence.value == 0f) return

    Box(
        modifier = Modifier
            // Clip only while the slot is growing or shrinking. A settled slot leaves its glyphs
            // unclipped, so a digit's blur and overshoot spill past its cell the way numericText's do.
            // The layer sits outside the layout below so it takes the shown width; inside, it would
            // take the glyph's full width and a growing digit would draw over its neighbours.
            .graphicsLayer { clip = presence.value < 1f }
            .layout { measurable, _ ->
                val p = measurable.measure(Constraints())
                val w = if (isDigit) digitWidthPx else p.width
                val shownWidth = (w * presence.value).roundToInt()
                layout(shownWidth, p.height) {
                    p.place((shownWidth - p.width) / 2, 0)
                }
            }
            .then(if (char == null) Modifier.clearAndSetSemantics { } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (isDigit) {
            if (outgoingMounted) {
                Text(
                    modifier = Modifier
                        .digitCell(digitWidthPx, bleed = MaxBlur)
                        .graphicsLayer {
                            val p = motion.progress
                            val d = startOffset.floatValue + (-dir.floatValue - startOffset.floatValue) * p
                            applyGlyphMotion(d, outgoingFade(p), p, presence.value)
                        }
                        .padding(MaxBlur),
                    text = outDigit.intValue.toString(),
                    style = style,
                    color = color,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            if (!motion.waiting) {
                Text(
                    modifier = Modifier
                        .digitCell(digitWidthPx, bleed = MaxBlur)
                        .graphicsLayer {
                            val p = motion.progress
                            val d = dir.floatValue * (1f - p)
                            applyGlyphMotion(d, incomingFade(p), p, presence.value)
                        }
                        .padding(MaxBlur),
                    text = inDigit.intValue.toString(),
                    style = style,
                    color = color,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        } else {
            Text(
                modifier = Modifier.graphicsLayer { alpha = fade.value * presence.value },
                text = shownChar.toString(),
                style = style,
                color = color,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/**
 * Lays a glyph out [widthPx] wide, centred, so every digit takes the same room. [bleed] is padding the
 * content carries on every side that should not count towards the cell's size: a glyph's blur layer is
 * padded by the blur radius because Android renders a `RenderEffect` into a layer the size of its
 * node, so without the room a blurred glyph is cut off square at its own edges.
 */
private fun Modifier.digitCell(widthPx: Int, bleed: Dp = 0.dp): Modifier = layout { measurable, _ ->
    val p = measurable.measure(Constraints())
    val bleedPx = bleed.roundToPx()
    layout(widthPx, p.height - 2 * bleedPx) {
        p.place((widthPx - p.width) / 2, -bleedPx)
    }
}

/**
 * Applies [glyphMotion] to a glyph's layer. The blur is a `RenderEffect`, which only exists from
 * API 31; below that the glyph keeps the short travel and the fade.
 */
private fun GraphicsLayerScope.applyGlyphMotion(d: Float, fade: Float, p: Float, presence: Float) {
    val motion = glyphMotion(d, fade, p, presence)
    // The layer carries [MaxBlur] of padding on each side; travel is in heights of the glyph itself.
    translationY = motion.travel * (size.height - 2 * MaxBlur.toPx())
    alpha = motion.alpha
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val radius = MaxBlur.toPx() * motion.blur
        renderEffect = if (radius > 0.5f) BlurEffect(radius, radius, TileMode.Decal) else null
    }
}

private val MaxBlur = 11.dp

private const val Digits = "0123456789"

private class DigitMetrics(val widest: Char, val widthPx: Int)

/** The widest digit in [style] and its width, from one layout of all ten. */
private fun TextMeasurer.digitMetrics(style: TextStyle): DigitMetrics {
    val result = measure(Digits, style)
    var widest = 0
    var width = 0f
    for (i in Digits.indices) {
        val w = result.getBoundingBox(i).width
        if (w > width) {
            width = w
            widest = i
        }
    }
    return DigitMetrics(Digits[widest], ceil(width).toInt())
}

private class CharHolder(var value: Char)

private class SlotTrack(var digit: Int, var symbol: Char? = null)

// Stiffness matches iOS's per-digit roll time measured off a 60fps recording of the chart readout.
// The damping is SwiftUI's `.snappy` (bounce 0.15), which that readout uses: a digit passes its rest
// slightly and settles back.
private val RollSpring = FloatSpringSpec(dampingRatio = 0.85f, stiffness = 380f)

/** A digit slot's roll: progress from the outgoing glyph (0) to the incoming one (1), stepped by [run]. */
private class RollMotion {
    var progress by mutableFloatStateOf(1f)
    var velocity = 0f
    var active = false
    var delayNanos = 0L
    private var startAtNanos = -1L

    /** True while a roll is waiting out its stagger delay; the incoming glyph isn't drawn yet. */
    var waiting by mutableStateOf(false)

    /** Bumped on every [retarget], so [run] restarts after it has settled. */
    var kicks by mutableIntStateOf(0)

    fun settle() {
        progress = 1f
        velocity = 0f
        active = false
        waiting = false
        delayNanos = 0L
        startAtNanos = -1L
    }

    fun retarget(velocity: Float) {
        progress = 0f
        this.velocity = velocity
        if (!active) {
            active = true
            waiting = delayNanos > 0
            kicks++
        }
    }

    suspend fun run(spec: FloatSpringSpec) {
        var last = -1L
        while (active) {
            withFrameNanos { now ->
                if (startAtNanos < 0) startAtNanos = now + delayNanos
                if (now < startAtNanos || last < 0) {
                    last = now
                    return@withFrameNanos
                }
                waiting = false
                val dt = now - last
                last = now
                val p = spec.getValueFromNanos(dt, progress, 1f, velocity)
                val v = spec.getVelocityFromNanos(dt, progress, 1f, velocity)
                if (abs(1f - p) < 0.001f && abs(v) < 0.01f) settle() else {
                    progress = p
                    velocity = v
                }
            }
        }
    }
}

private class DirectionHolder(var last: String, var rising: Boolean = true)
