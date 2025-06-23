package com.flipcash.app.pools.internal.list.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.flipcash.features.pools.R
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.CodeChip

@Composable
internal fun PoolStatusSeparator(
    item: PoolListItem.Header,
    modifier: Modifier = Modifier,
) {
    CodeChip(
        label = when (item) {
            PoolListItem.Header.Open -> stringResource(R.string.title_open)
            PoolListItem.Header.Completed -> stringResource(R.string.title_completed)
        },
        modifier = modifier.padding(
            horizontal = CodeTheme.dimens.inset,
            vertical = CodeTheme.dimens.grid.x3
        ),
        contentColor = CodeTheme.colors.textSecondary
    )
}