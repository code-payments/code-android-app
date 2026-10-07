package com.flipcash.shared.common.ui.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.getcode.theme.CodeTheme
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.materials.HazeMaterials

/**
 * The soft edge under a profile's app bar: the page blurs in toward the top of the screen rather
 * than stopping at a hard line, as iOS's soft scroll edge does. Like iOS
 * (`hidesTopScrollEdge(untilOffset:)`), it only shows once the cover has scrolled halfway up, so
 * the cover reads clean at rest.
 *
 * Place it over the scroll content and under the app bar, reading the same [hazeState] the content
 * is a source for.
 */
@Composable
fun ProfileTopScrollEdge(
    hazeState: HazeState,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val threshold = with(density) { (ProfileCoverHeight / 2).toPx() }
    val visible by remember(threshold) { derivedStateOf { scrollState.value > threshold } }
    // The status bar and the app bar's row of buttons.
    val height = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp
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
