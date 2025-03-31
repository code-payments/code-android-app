package com.getcode.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import com.getcode.theme.CodeTheme

@Composable
fun TextSection(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2)) {
        Text(
            text = title,
            style = CodeTheme.typography.textLarge
        )
        Text(
            text = description,
            style = CodeTheme.typography.textSmall
        )
    }
}