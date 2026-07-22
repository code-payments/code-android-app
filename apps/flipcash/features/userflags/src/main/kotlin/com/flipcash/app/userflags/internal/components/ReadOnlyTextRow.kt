package com.flipcash.app.userflags.internal.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.flipcash.app.userflags.internal.ReadOnlyTextEntry
import com.getcode.theme.CodeTheme

@Composable
internal fun ReadOnlyTextRow(
    entry: ReadOnlyTextEntry,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Column(
            modifier = Modifier.padding(
                horizontal = CodeTheme.dimens.grid.x3,
                vertical = CodeTheme.dimens.grid.x3,
            ),
        ) {
            Text(
                text = stringResource(entry.label),
                style = CodeTheme.typography.textLarge,
                color = CodeTheme.colors.textMain,
            )
            Text(
                text = entry.value,
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = CodeTheme.dimens.inset),
            color = CodeTheme.colors.divider,
            thickness = 0.5.dp,
        )
    }
}
