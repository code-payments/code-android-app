package com.getcode.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.getcode.theme.CodeTheme

@Composable
fun TextSection(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2)) {
        Text(
            text = title,
            style = CodeTheme.typography.subtitle1
        )
        Text(
            text = description,
            style = CodeTheme.typography.body2
        )
    }
}