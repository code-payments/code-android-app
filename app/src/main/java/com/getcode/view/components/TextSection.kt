package com.getcode.view.components

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
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = CodeTheme.typography.h6.copy(
                fontWeight = FontWeight.Bold,
            )
        )
        Text(
            text = description,
            style = CodeTheme.typography.subtitle2
        )
    }
}