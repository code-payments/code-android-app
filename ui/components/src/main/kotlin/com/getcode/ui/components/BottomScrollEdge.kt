package com.getcode.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.getcode.theme.CodeTheme
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.materials.HazeMaterials

/**
 * [TopScrollEdge] turned over, for a page with nothing pinned to its foot: content blurs out toward
 * the bottom of the screen as it scrolls under the navigation bar.
 *
 * Place it over the scroll content, aligned to the bottom, reading the same [hazeState] the content
 * is a source for. It shows while [scrollState] has more to scroll to, so the page's last line reads
 * clean once it is reached. [height] defaults to the navigation bar plus [fade].
 */
@Composable
fun BottomScrollEdge(
    hazeState: HazeState,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    fade: Dp = 40.dp,
    height: Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + fade,
) {
    val visible by remember(scrollState) { derivedStateOf { scrollState.canScrollForward } }
    val material = HazeMaterials.ultraThin(containerColor = CodeTheme.colors.background)

    AnimatedVisibility(visible = visible, modifier = modifier, enter = fadeIn(), exit = fadeOut()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .hazeBlur(
                    HazeInput.Sources(hazeState),
                    material.then {
                        progressive(HazeProgressive.verticalGradient(startIntensity = 0f, endIntensity = 1f))
                    },
                ),
        )
    }
}
