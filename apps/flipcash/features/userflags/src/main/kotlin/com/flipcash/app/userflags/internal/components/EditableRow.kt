package com.flipcash.app.userflags.internal.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.flipcash.app.userflags.internal.EditableEntry
import com.getcode.theme.CodeTheme
import androidx.compose.foundation.clickable

@Composable
internal fun EditableRow(
    entry: EditableEntry<*>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = modifier
            .clickable { onClick() }
            .padding(horizontal = CodeTheme.dimens.grid.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = CodeTheme.dimens.grid.x3),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
            ) {
                Text(
                    text = stringResource(entry.field.label),
                    style = CodeTheme.typography.textLarge,
                    color = CodeTheme.colors.textMain,
                )
                if (entry.resolved.isOverridden) {
                    Box(
                        modifier = Modifier
                            .size(CodeTheme.dimens.grid.x1)
                            .background(
                                color = CodeTheme.colors.betaIndicator,
                                shape = CircleShape,
                            )
                    )
                }
            }

            if (entry.resolved.isOverridden) {
                Text(
                    text = "Server: ${entry.formattedServerValue()} → ${entry.formattedEffectiveValue()}",
                    style = CodeTheme.typography.textSmall,
                    color = CodeTheme.colors.textSecondary,
                )
            } else {
                Text(
                    text = "Server: ${entry.formattedServerValue()}",
                    style = CodeTheme.typography.textSmall,
                    color = CodeTheme.colors.textSecondary,
                )
            }
        }
    }

    HorizontalDivider(
        modifier = Modifier.padding(horizontal = CodeTheme.dimens.inset),
        color = CodeTheme.colors.divider,
        thickness = 0.5.dp,
    )
}