package com.getcode.ui.components.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.getcode.theme.CodeTheme
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

/**
 * Layout shared by the chrome that floats over the bottom of the screen: the navigation bar and the
 * toast that rises out of it.
 */
object FloatingChrome {
    /**
     * The inset from each screen edge. The design insets the bar 24pt (node 10642:1325), which keeps
     * each tab's pill wider than tall; 25 is the nearest fixed step. A toast takes the same inset, so
     * it is never wider than the bar.
     */
    val horizontalInset: Dp
        @Composable get() = CodeTheme.dimens.staticGrid.x5
}

/**
 * The frosted "liquid glass" surface of the floating chrome, clipped to [shape].
 *
 * A wide blur of what lies beneath, plus a strong tint toward a grey lifted off the (near-black)
 * background at high alpha. Over empty or dark content it reads as a light frosted glass sitting
 * above the screen; over the vibrant cards the high alpha mutes their colour toward that same grey.
 * A faint bright rim gives the glass its edge.
 *
 * [hazeState] must belong to a `hazeSource` that does not contain this surface, or there is nothing
 * to blur. With no [hazeState] the surface is a near-opaque fill of the same tint.
 */
@Composable
fun Modifier.floatingGlass(
    hazeState: HazeState?,
    shape: Shape = CircleShape,
): Modifier {
    val backdrop = CodeTheme.colors.background
    val glassTint = lerp(backdrop, Color.White, 0.18f)
    val fill = if (hazeState != null) {
        // The HazeBlurStyle builder is not a @Composable scope, so theme reads are hoisted above it.
        val liquidGlass = HazeBlurStyle {
            blurRadius(32.dp)
            backgroundColor(backdrop)
            colorEffects(listOf(HazeColorEffect.tint(glassTint.copy(alpha = 0.72f))))
        }
        Modifier.hazeBlur(HazeInput.Sources(hazeState), liquidGlass)
    } else {
        Modifier.background(glassTint.copy(alpha = 0.9f), shape)
    }
    // `clip` must precede `hazeBlur` to bound the blur to the shape, not its bounding box.
    return this
        .clip(shape)
        .then(fill)
        .border(CodeTheme.dimens.border, Color.White.copy(alpha = 0.08f), shape)
}
