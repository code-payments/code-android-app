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
    private val FadeTail = 16.dp

    /**
     * How far short of the status bar's bottom edge the fade starts, so the strip behind the clock
     * and battery is solid background rather than a nearly-opaque scrim with a bubble under it.
     * iOS's `opaqueLength`, verbatim.
     */
    private val OpaqueInsetTrim = 12.dp

    /**
     * How far past the bar the blur takes to feather back to sharp. Everything above that boundary is
     * blurred at full strength — iOS's `.soft` edge effect covers its whole region evenly and softens
     * only at the edge, so a linear ramp from the very top leaves the title's depth barely touched.
     */
    private val BlurTail = 24.dp

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
    fun blurHold(barHeight: Dp): Dp = barHeight

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
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to containerColor,
                    hold to containerColor,
                    1f to Color.Transparent,
                ),
                startY = 0f,
                endY = height,
            ),
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
        val stops = floatArrayOf(0f, holdPx / totalPx, 1f)

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
        return RenderEffect
            .createBlendModeEffect(sharpRest, blurredTop, AndroidBlendMode.SRC_OVER)
            .asComposeRenderEffect()
    }

    /**
     * A mask running the height of the effect: opaque for the hold and falling off across the tail,
     * or the exact complement of that, so the two together cover every pixel once.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun verticalMask(
        totalPx: Float,
        stops: FloatArray,
        opaqueFirst: Boolean,
    ): RenderEffect {
        val near = if (opaqueFirst) AndroidColor.BLACK else AndroidColor.TRANSPARENT
        val far = if (opaqueFirst) AndroidColor.TRANSPARENT else AndroidColor.BLACK
        return RenderEffect.createShaderEffect(
            LinearGradient(
                0f, 0f, 0f, totalPx,
                intArrayOf(near, near, far),
                stops,
                Shader.TileMode.CLAMP,
            )
        )
    }
}
