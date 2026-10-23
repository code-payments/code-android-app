package com.getcode.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.getcode.theme.CodeTheme
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.materials.HazeMaterials

/**
 * The soft edge under an app bar: content blurs in toward the top of the screen as it scrolls under
 * the bar, rather than stopping at a hard line or fading out, as iOS's soft scroll edge
 * (`scrollEdgeEffectStyle(.soft)`) does.
 *
 * Place it over the scroll content and under the app bar, [height] tall (the bar's height), reading
 * the same [hazeState] the content is a source for. It shows once [scrollState] has scrolled past
 * [threshold]; at the default of 0, as soon as anything can sit under the bar.
 */
@Composable
fun TopScrollEdge(
    hazeState: HazeState,
    scrollState: ScrollState,
    height: Dp,
    modifier: Modifier = Modifier,
    threshold: Dp = 0.dp,
) {
    val thresholdPx = with(LocalDensity.current) { threshold.toPx() }
    val visible by remember(thresholdPx) { derivedStateOf { scrollState.value > thresholdPx } }
    TopScrollEdge(hazeState = hazeState, visible = visible, height = height, modifier = modifier)
}

/** [TopScrollEdge] for a lazy list; [threshold] is measured within its first item. */
@Composable
fun TopScrollEdge(
    hazeState: HazeState,
    listState: LazyListState,
    height: Dp,
    modifier: Modifier = Modifier,
    threshold: Dp = 0.dp,
) {
    val thresholdPx = with(LocalDensity.current) { threshold.toPx() }
    val visible by remember(thresholdPx) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > thresholdPx
        }
    }
    TopScrollEdge(hazeState = hazeState, visible = visible, height = height, modifier = modifier)
}

/** [TopScrollEdge] shown whenever [visible] is true. */
@Composable
fun TopScrollEdge(
    hazeState: HazeState,
    visible: Boolean,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    val material = HazeMaterials.ultraThin(containerColor = CodeTheme.colors.background)

    AnimatedVisibility(visible = visible, modifier = modifier, enter = fadeIn(), exit = fadeOut()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .hazeBlur(
                    HazeInput.Sources(hazeState),
                    material.then {
                        progressive(HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f))
                    },
                ),
        )
    }
}
