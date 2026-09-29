package com.getcode.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import com.getcode.theme.CodeTheme

/**
 * An icon followed by a line of text, for lists of short statements.
 *
 * Pass [Color.Unspecified] as [iconTint] for an icon that carries its own colours.
 */
@Composable
fun BulletRow(
    painter: Painter,
    text: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = CodeTheme.typography.textSmall,
    textColor: Color = CodeTheme.colors.textSecondary,
    iconTint: Color = CodeTheme.colors.textMain,
    spacing: Dp = CodeTheme.dimens.grid.x2,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painter = painter, contentDescription = null, tint = iconTint)
        Text(
            text = text,
            style = textStyle,
            color = textColor,
        )
    }
}
