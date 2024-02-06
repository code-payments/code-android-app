package com.getcode.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.getcode.theme.CodeTheme
import com.getcode.ui.utils.circleBackground


@Composable
fun Badge(
    modifier: Modifier = Modifier,
    count: Int,
    color: Color = CodeTheme.colors.brand,
    contentColor: Color = Color.White,
) {
    AnimatedVisibility(visible = count > 0) {
        val text = when {
            count in 1..99 -> "$count"
            else -> "99+"
        }

        Text(
            text = text,
            color = contentColor,
            style = CodeTheme.typography.body1.copy(fontWeight = FontWeight.W700),
            modifier = modifier.circleBackground(color = color, padding = CodeTheme.dimens.grid.x1)
        )
    }
}