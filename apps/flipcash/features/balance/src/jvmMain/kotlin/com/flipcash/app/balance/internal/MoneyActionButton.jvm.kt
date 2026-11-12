package com.flipcash.app.balance.internal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.getcode.theme.CodeTheme

// Desktop has no CodeButton: the label alone, still tappable.
@Composable
internal actual fun MoneyActionButton(
    text: String,
    primary: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) = Box(
    modifier = modifier
        .clickable(onClick = onClick)
        .padding(vertical = CodeTheme.dimens.grid.x2),
    contentAlignment = Alignment.Center,
) {
    Text(text = text, style = CodeTheme.typography.textMedium, color = CodeTheme.colors.textMain)
}
