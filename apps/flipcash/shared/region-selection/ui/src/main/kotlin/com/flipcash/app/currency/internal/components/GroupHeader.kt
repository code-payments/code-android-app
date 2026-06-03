package com.flipcash.app.currency.internal.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.getcode.theme.CodeTheme

@Composable
internal fun GroupHeader(
    text: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = CodeTheme.dimens.inset)
        ) {
            Text(
                modifier = Modifier.padding(bottom = CodeTheme.dimens.grid.x2),
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
                text = text
            )
        }
        HorizontalDivider(
            color = CodeTheme.colors.dividerVariant,
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
        )
    }
}