package com.getcode.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.getcode.theme.CodeTheme

@Composable
fun VerticalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = CodeTheme.dimens.border,
    color: Color = CodeTheme.colors.brandLight,
) = Canvas(modifier.fillMaxHeight().width(thickness)) {
    drawLine(
        color = color,
        strokeWidth = thickness.toPx(),
        start = Offset(thickness.toPx() / 2, 0f),
        end = Offset(thickness.toPx() / 2, size.height),
    )
}