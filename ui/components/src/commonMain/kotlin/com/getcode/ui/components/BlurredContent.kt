package com.getcode.ui.components

import androidx.annotation.IntRange
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.getcode.theme.CodeTheme
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/** How long the blur takes to come on or off. Long enough to read as a lift, short enough to wait. */
private const val BlurFadeDurationMillis = 400

@Composable
fun BlurredContent(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @IntRange(from = 0, to = 25) radius: Int = 25,
    containerColor: Color = CodeTheme.colors.background,
    content: @Composable BoxScope.() -> Unit
) {
    // One value drives the radius and the layer's own opacity, because the material carries a tint
    // as well as a blur: shrinking the radius alone leaves a flat scrim over sharp content and then
    // pops it away at the end of the animation.
    val progress by animateFloatAsState(
        targetValue = if (enabled) 1f else 0f,
        // On at once, off over time. A blur is a withholding, so the content under it must never be
        // legible first — a screen that works out what to hide a frame after it draws would
        // otherwise animate a readable surface into a covered one. Lifting it is the transition.
        animationSpec = if (enabled) snap() else tween(durationMillis = BlurFadeDurationMillis),
        label = "blur progress",
    )

    val hazeState = rememberHazeState()
    val material = HazeMaterials.regular(containerColor = containerColor)

    Box(modifier) {
        Box(Modifier.hazeSource(hazeState)) {
            content()
        }
        // Mounted until the fade has actually run. Tying this to [enabled] removed the layer on the
        // same frame the flag flipped, so the animation had nothing left to draw and the blur came
        // off in one step.
        if (progress > 0f) {
            Box(
                Modifier
                    .matchParentSize()
                    .alpha(progress)
                    .hazeBlur(
                        input = HazeInput.Sources(hazeState),
                        style = material.then { blurRadius(radius.dp * progress) },
                    )
            )
        }
    }
}
