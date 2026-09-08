package com.flipcash.app.messenger.internal.screens.components

import android.graphics.LinearGradient
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
     * How far above the bar's bottom edge the fade finishes. iOS clears 8pt above a 44pt bar; the
     * point is that the fade is spent by the time the title is passed, not that content is hidden
     * all the way down. The blur carries the last of the transition.
     */
    private val FadeEndInset = 10.dp

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

    /** Matched to the bottom bar's `ultraThin` material, so both edges soften by the same amount. */
    private val BlurRadius = 20.dp

    /** The region the fade covers, given the bar's measured height. */
    fun fadeHeight(barHeight: Dp): Dp = (barHeight - FadeEndInset).coerceAtLeast(0.dp)

    /** The region the blur covers at full strength, before the tail feathers it out. */
    fun blurHold(barHeight: Dp): Dp = barHeight

    /**
     * Opaque behind the status bar, then a straight ramp to transparent — iOS's three stops. Alpha
     * lands near a third behind the title, which is only legible because the blur is under it.
     */
    fun fadeBrush(containerColor: Color, statusBars: Dp, fadeHeight: Dp): Brush {
        val hold = when {
            fadeHeight <= 0.dp -> 0f
            else -> ((statusBars - OpaqueInsetTrim) / fadeHeight).coerceIn(0f, 1f)
        }
        return Brush.verticalGradient(
            colorStops = arrayOf(
                0f to containerColor,
                hold to containerColor,
                1f to Color.Transparent,
            )
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
     * A blurred copy of the layer, masked to the top of it, drawn over the sharp original. The mask is
     * what shapes the blur: opaque for [holdPx], then falling to nothing across [tailPx], so the
     * blurred copy shows through completely under the bar and not at all below the tail.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun softTopEdgeEffect(
        holdPx: Float,
        tailPx: Float,
        radiusPx: Float,
    ): androidx.compose.ui.graphics.RenderEffect {
        val blurred = RenderEffect.createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP)
        val totalPx = holdPx + tailPx
        val mask = RenderEffect.createShaderEffect(
            LinearGradient(
                0f, 0f, 0f, totalPx,
                intArrayOf(AndroidColor.BLACK, AndroidColor.BLACK, AndroidColor.TRANSPARENT),
                floatArrayOf(0f, holdPx / totalPx, 1f),
                Shader.TileMode.CLAMP,
            )
        )
        val topOnly =
            RenderEffect.createBlendModeEffect(blurred, mask, AndroidBlendMode.DST_IN)
        val sharp = RenderEffect.createOffsetEffect(0f, 0f)
        return RenderEffect
            .createBlendModeEffect(sharp, topOnly, AndroidBlendMode.SRC_OVER)
            .asComposeRenderEffect()
    }
}
