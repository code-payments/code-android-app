package com.flipcash.app.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import com.getcode.theme.CodeTheme

@Composable
fun ReceiptLineItem(
    label: String,
    amount: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = AnnotatedString(label),
            inlineContent = emptyMap(),
            style = CodeTheme.typography.textSmall,
            color = CodeTheme.colors.textSecondary,
        )
        Text(
            text = amount,
            style = CodeTheme.typography.textMedium,
            color = CodeTheme.colors.textMain,
        )
    }
}

@Composable
fun ReceiptLineItem(
    label: AnnotatedString,
    amount: String,
    modifier: Modifier = Modifier,
    inlineContentMap: Map<String, InlineTextContent> = mapOf(),
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            inlineContent = inlineContentMap,
            style = CodeTheme.typography.textSmall,
            color = CodeTheme.colors.textSecondary,
        )
        Text(
            text = amount,
            style = CodeTheme.typography.textMedium,
            color = CodeTheme.colors.textMain,
        )
    }
}