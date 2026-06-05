package com.flipcash.app.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import com.getcode.theme.CodeTheme
import com.getcode.theme.extraSmall
import androidx.compose.foundation.clickable

@Composable
fun TileButton(
    text: String,
    icon: Painter,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(vertical = CodeTheme.dimens.grid.x6),
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(CodeTheme.shapes.extraSmall)
            .background(
                color = CodeTheme.colors.surfaceVariant,
                shape = CodeTheme.shapes.extraSmall,
            )
            .clickable(onClick = onClick)
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                modifier = Modifier.size(32.dp),
                painter = icon,
                contentDescription = null,
                colorFilter = ColorFilter.tint(CodeTheme.colors.textMain),
            )
            Text(
                text = text,
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.textMain,
            )
        }
    }
}