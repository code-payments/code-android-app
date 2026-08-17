package com.getcode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.getcode.ui.components.AppBarDefaults.IconSize
import androidx.compose.foundation.clickable
import com.getcode.theme.CodeTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.hazeEffect

private val ButtonSize: Dp
    @Composable get() = CodeTheme.dimens.staticGrid.x8
private val ButtonBackground = Color.White.copy(alpha = 0.1f)


@Composable
fun CircularIconButton(
    modifier: Modifier = Modifier,
    imageSize: Dp = IconSize,
    buttonSize: Dp = ButtonSize,
    hazeState: HazeState? = null,
    onClick: () -> Unit,
    testTag: String? = null,
    content: @Composable (Dp) -> Unit,
) {
    // When a HazeState is supplied the button frosts whatever content scrolls beneath the app bar
    // (iOS "liquid glass"), matching the v2 nav pill; `clip` precedes `hazeEffect` so the blur is
    // bounded to the circle. Falls back to the flat translucent fill when no HazeState is supplied.
    val fill = if (hazeState != null) {
        val glassTint = lerp(CodeTheme.colors.background, Color.White, 0.18f)
        val liquidGlass = HazeBlurStyle(
            blurRadius = CodeTheme.dimens.grid.x4,
            backgroundColor = CodeTheme.colors.background,
            colorEffect = HazeColorEffect.tint(glassTint.copy(alpha = 0.72f)),
        )
        Modifier
            .clip(CircleShape)
            .hazeEffect(hazeState) { blurEffect { style = liquidGlass } }
            .border(CodeTheme.dimens.border, Color.White.copy(alpha = 0.08f), CircleShape)
    } else {
        Modifier
            .background(ButtonBackground, CircleShape)
            .clip(CircleShape)
    }

    Box(
        modifier = modifier
            .size(buttonSize)
            .then(fill)
            .clickable { onClick() }
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        content(imageSize)
    }
}