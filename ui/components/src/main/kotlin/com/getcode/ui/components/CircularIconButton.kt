package com.getcode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.getcode.ui.components.AppBarDefaults.IconSize
import androidx.compose.foundation.clickable
import com.getcode.theme.CodeTheme

private val ButtonSize: Dp
    @Composable get() = CodeTheme.dimens.staticGrid.x8
private val ButtonBackground = Color.White.copy(alpha = 0.1f)


@Composable
fun CircularIconButton(
    modifier: Modifier = Modifier,
    imageSize: Dp = IconSize,
    buttonSize: Dp = ButtonSize,
    onClick: () -> Unit,
    testTag: String? = null,
    content: @Composable (Dp) -> Unit,
) {
    Box(
        modifier = modifier
            .size(buttonSize)
            .background(ButtonBackground, CircleShape)
            .clip(CircleShape)
            .clickable { onClick() }
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        content(imageSize)
    }
}