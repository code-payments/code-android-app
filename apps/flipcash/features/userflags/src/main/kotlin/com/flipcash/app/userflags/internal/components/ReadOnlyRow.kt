package com.flipcash.app.userflags.internal.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.flipcash.app.userflags.internal.ReadOnlyEntry
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.SettingsSwitchRow

@Composable
internal fun ReadOnlyRow(
    entry: ReadOnlyEntry,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        SettingsSwitchRow(
            enabled = false,
            title = stringResource(entry.label),
            checked = entry.value,
            onClick = {},
        )

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = CodeTheme.dimens.inset),
            color = CodeTheme.colors.divider,
            thickness = 0.5.dp,
        )
    }
}