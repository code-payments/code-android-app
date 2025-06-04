package com.flipcash.app.withdrawal.internal.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import com.getcode.theme.CodeTheme

@Composable
internal fun DestinationBox(
    destination: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = CodeTheme.dimens.border,
                color = CodeTheme.colors.border,
                shape = CodeTheme.shapes.medium
            )
            .background(Color(0xFF071F10), CodeTheme.shapes.medium)
            .padding(CodeTheme.dimens.grid.x4)
            .then(modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = destination,
            style = CodeTheme.typography.textLarge.copy(textAlign = TextAlign.Center)
        )
    }
}