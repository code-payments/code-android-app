package com.getcode.ui.components.text

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.getcode.theme.CodeTheme

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title.uppercase(),
        style = CodeTheme.typography.textSmall,
        color = CodeTheme.colors.textSecondary,
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = CodeTheme.dimens.inset,
                end = CodeTheme.dimens.inset,
                top = CodeTheme.dimens.grid.x4,
                bottom = CodeTheme.dimens.grid.x1,
            )
    )
}