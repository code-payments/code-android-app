package com.flipcash.app.messenger.internal.screens.components

import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.graphics.BlendMode as AndroidBlendMode
import android.graphics.Color as AndroidColor

/**
 * How the transcript meets the top bar: the content passing under it is blurred, and a gradient over
 * that takes it to the background colour.
 *
 * Both halves are carried over from iOS, where the bar has no background of its own. The soft
 * scroll-edge effect blurs what scrolls under it but does not darken it, and a cash card's amount is
 * large white text — blurred, it still reads over the title — so `TranscriptTopFade` supplies the
 * darkening. Neither half works alone: without the blur the fade has to be strong enough to hide
 * content outright, and without the fade the amount stays legible.
 */
internal object ChatTopEdge {

    /**
     * How far past the bar's bottom edge the fade runs. iOS finishes just short of that edge, which
     * leaves alpha near a third behind the title. The extra distance both raises alpha there and
     * keeps the falloff from reading as a band across whichever bubble it lands on.
     */
    private val FadeTail = 36.dp

    /**
     * How far short of the status bar's bottom edge the fade starts, so the strip behind the clock
     * and battery is solid background rather than a nearly-opaque scrim with a bubble under it.
     * iOS's `opaqueLength`, verbatim.
     */
    private val OpaqueInsetTrim = 12.dp

    /**
     * How far the blur takes to feather back to sharp. Everything above that falloff is blurred at
     * full strength — iOS's `.soft` edge effect covers its whole region evenly and softens only at
     * the edge, so a ramp starting at the very top leaves the title's depth barely touched.
     *
     * Long, because this is the distance over which a bubble goes from unreadable to readable and
     * the eye follows it the whole way. Over a short one the same change of state arrives as an
     * event rather than as a transition.
     */
    private val BlurTail = 64.dp

    /**
     * How far above the bar's bottom edge the blur starts easing off. Holding full strength right to
     * that edge means the falloff begins exactly where a bubble emerges, so the two coincide and
     * read as one hard boundary; starting earlier separates them.
     */
    private val BlurHoldTrim = 16.dp

    /**
     * Deeper than the bottom bar's `ultraThin` material. The bottom bar sits over ordinary bubbles;
     * this one has to hold up against an amount, so it needs the extra radius to stop the digits
     * resolving through it.
     */
    private val BlurRadius = 28.dp

    /**
     * How much of the blurred copy's brightness survives.
     *
     * A blur spreads a pixel's light over its radius, so white text on a dark ground comes back as a
     * halo covering more area than the glyphs did — the wider the radius, the more it reads as a
     * glow rather than as something out of focus. Scaling the blurred copy down before compositing
     * takes the light back out, which is a different job from the fade: the fade mixes towards the
     * background evenly, while this only touches what the blur lit up.
     */
    private const val BlurBrightness = 0.6f

    /** The region the blur covers at full strength, before the tail feathers it out. */
    fun blurHold(barHeight: Dp): Dp = (barHeight - BlurHoldTrim).coerceAtLeast(0.dp)

    /**
     * Where a ramp has got to, a fraction of the way along it.
     *
     * Smoothstep rather than a straight line. A linear ramp turns a corner at each end — full
     * strength one pixel, already dropping the next — and those corners are what the eye picks out
     * as the edges of a band, however long the ramp between them is. This leaves and arrives flat.
     */
    private fun eased(t: Float): Float = t * t * (3f - 2f * t)

    /** Fractions along a ramp at which to sample [eased], enough for the steps not to show. */
    private val RampSamples = floatArrayOf(0f, 0.15f, 0.3f, 0.45f, 0.6f, 0.75f, 0.9f, 1f)

    /**
     * Draws the fade behind the bar: opaque to [containerColor] behind the status bar, then a
     * straight ramp to transparent — iOS's three stops, run on past the bar's own bottom edge.
     *
     * Drawn rather than laid out because the ramp is longer than the bar. A sibling of that height
     * would grow the top bar's slot, and the scaffold turns that slot's height into the transcript's
     * content padding, so a purely visual tail would push every message down.
     */
    fun Modifier.topFade(containerColor: Color, statusBars: Dp): Modifier = drawBehind {
        val height = size.height + FadeTail.toPx()
        if (height <= 0f) return@drawBehind
        val hold = ((statusBars.toPx() - OpaqueInsetTrim.toPx()) / height).coerceIn(0f, 1f)
        val stops = Array(RampSamples.size + 1) { index ->
            if (index == 0) {
                0f to containerColor
            } else {
                val t = RampSamples[index - 1]
                (hold + t * (1f - hold)) to containerColor.copy(alpha = 1f - eased(t))
            }
        }
        drawRect(
            brush = Brush.verticalGradient(colorStops = stops, startY = 0f, endY = height),
            size = Size(size.width, height),
        )
    }

    /**
     * Blurs the top [height] of whatever this is applied to, easing back to sharp over that
     * distance — the counterpart of iOS's `topEdgeEffect.style = .soft`.
     *
     * `RenderEffect` arrived in API 31, so below that the fade is on its own. That is the same
     * trade the platform makes elsewhere, and the fade alone still keeps an amount out of the status
     * bar; it just stops short of hiding it under the title.
     */
    @Composable
    fun Modifier.softTopEdge(hold: Dp): Modifier {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return this
        val density = LocalDensity.current
        val holdPx = with(density) { hold.toPx() }
        val tailPx = with(density) { BlurTail.toPx() }
        val radiusPx = with(density) { BlurRadius.toPx() }
        if (holdPx <= 0f || radiusPx <= 0f) return this
        val effect = remember(holdPx, tailPx, radiusPx) { softTopEdgeEffect(holdPx, tailPx, radiusPx) }
        return graphicsLayer {
            renderEffect = effect
            // The blur samples beyond the layer without this, dragging the bars' pixels inward.
            clip = true
        }
    }

    /**
     * The layer's top swapped for a blurred copy of itself: blurred where the mask is opaque, sharp
     * where it is clear, crossfading between the two across the tail.
     *
     * Both halves have to be masked. Drawing the blurred copy over an untouched original instead
     * leaves the original showing through it — the transcript's own background is transparent, and
     * so is a blurred glyph's spread — which reads as a sharp glyph wearing a halo rather than as
     * one out of focus. On white text over a dark ground that halo is a glow.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun softTopEdgeEffect(
        holdPx: Float,
        tailPx: Float,
        radiusPx: Float,
    ): androidx.compose.ui.graphics.RenderEffect {
        val totalPx = holdPx + tailPx
        val hold = holdPx / totalPx
        val stops = FloatArray(RampSamples.size + 1) { index ->
            if (index == 0) 0f else hold + RampSamples[index - 1] * (1f - hold)
        }

        val blurred = RenderEffect.createColorFilterEffect(
            ColorMatrixColorFilter(
                floatArrayOf(
                    BlurBrightness, 0f, 0f, 0f, 0f,
                    0f, BlurBrightness, 0f, 0f, 0f,
                    0f, 0f, BlurBrightness, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                )
            ),
            RenderEffect.createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP),
        )
        val blurredTop = RenderEffect.createBlendModeEffect(
            blurred,
            verticalMask(totalPx, stops, opaqueFirst = true),
            AndroidBlendMode.DST_IN,
        )
        val sharpRest = RenderEffect.createBlendModeEffect(
            RenderEffect.createOffsetEffect(0f, 0f),
            verticalMask(totalPx, stops, opaqueFirst = false),
            AndroidBlendMode.DST_IN,
        )
        // PLUS, not SRC_OVER: the two masks are complements, so adding them weights each pixel
        // between the copies exactly once. Compositing one over the other would put the lower
        // through its own mask a second time, dipping the transition darker in the middle.
        return RenderEffect
            .createBlendModeEffect(sharpRest, blurredTop, AndroidBlendMode.PLUS)
            .asComposeRenderEffect()
    }

    /**
     * A mask running the height of the effect: opaque for the hold and easing off across the tail,
     * or the exact complement of that, so the two together cover every pixel once.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun verticalMask(
        totalPx: Float,
        stops: FloatArray,
        opaqueFirst: Boolean,
    ): RenderEffect {
        val colors = IntArray(stops.size) { index ->
            val covered = if (index == 0) 0f else eased(RampSamples[index - 1])
            val alpha = if (opaqueFirst) 1f - covered else covered
            AndroidColor.argb((alpha * 255f).toInt(), 0, 0, 0)
        }
        return RenderEffect.createShaderEffect(
            LinearGradient(0f, 0f, 0f, totalPx, colors, stops, Shader.TileMode.CLAMP)
        )
    }
}
