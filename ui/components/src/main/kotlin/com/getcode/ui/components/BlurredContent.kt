package com.getcode.ui.components

import androidx.annotation.IntRange
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.getcode.theme.CodeTheme
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@Composable
fun Cloudy(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @IntRange(from = 0, to = 25) radius: Int = 25,
    containerColor: Color = CodeTheme.colors.background,
    content: @Composable BoxScope.() -> Unit
) {
    val blurRadius by animateDpAsState(
        targetValue = if (enabled) radius.dp else 0.dp,
        label = "blur radius"
    )

    val hazeState = rememberHazeState()
    val material = HazeMaterials.regular(containerColor = containerColor)

    Box(modifier) {
        Box(Modifier.hazeSource(hazeState)) {
            content()
        }
        if (enabled) {
            Box(
                Modifier
                    .matchParentSize()
                    .hazeEffect(hazeState) {
                        blurEffect {
                            this.blurRadius = blurRadius
                            style = material
                        }
                    }
            )
        }
    }
}
