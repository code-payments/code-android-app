package com.getcode.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.getcode.theme.CodeTheme

/**
 * The centered title-and-caption block a list screen shows when it has nothing to list. It is the
 * layout the Blocked and Trusted Websites screens draw; place it with [modifier], usually
 * `Modifier.align(Alignment.Center)` inside a full-size `Box`.
 */
@Composable
fun EmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = CodeTheme.dimens.inset),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
    ) {
        Text(
            text = title,
            style = CodeTheme.typography.textLarge,
            color = CodeTheme.colors.textMain,
        )
        Text(
            modifier = Modifier.fillMaxWidth(0.8f),
            text = description,
            style = CodeTheme.typography.caption,
            color = CodeTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}
