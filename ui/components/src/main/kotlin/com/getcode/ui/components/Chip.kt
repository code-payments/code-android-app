package com.getcode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.getcode.theme.CodeTheme

@Composable
fun CodeChip(
    label: String,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = CodeTheme.dimens.grid.x2,
        vertical = CodeTheme.dimens.grid.x1
    ),
    backgroundColor: Color = CodeTheme.colors.surfaceVariant,
    contentColor: Color = CodeTheme.colors.textMain,
) {
    CodeChip(
        modifier = modifier,
        backgroundColor = backgroundColor,
        shape = shape,
        contentPadding = contentPadding,
    ) {
        Text(
            text = label,
            style = CodeTheme.typography.textSmall,
            color = contentColor,
        )
    }
}

@Composable
fun CodeChip(
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = CodeTheme.dimens.grid.x2,
        vertical = CodeTheme.dimens.grid.x1
    ),
    backgroundColor: Color = CodeTheme.colors.surfaceVariant,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .background(
                color = backgroundColor,
                shape = shape,
            ).padding(contentPadding),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = verticalAlignment,
    ) {
        content()
    }
}