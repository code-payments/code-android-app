package com.flipcash.app.pools.internal.list.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.flipcash.app.core.extensions.toYesOrNo
import com.flipcash.app.core.pools.PoolResolution
import com.flipcash.features.pools.R
import com.getcode.opencode.model.financial.Fiat
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.CodeChip

@Composable
internal fun CompletedPoolStatusRow(
    winnings: Fiat,
    buyIn: Fiat,
    didWin: Boolean,
    resolution: PoolResolution,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
    ) {
        when (resolution) {
            is PoolResolution.BooleanResolution -> {
                CodeChip(
                    shape = CodeTheme.shapes.small,
                    label = stringResource(R.string.subtitle_booleanResult, resolution.value.toYesOrNo()),
                    contentColor = CodeTheme.colors.textSecondary,
                )
            }
            is PoolResolution.Refund -> {
                CodeChip(
                    shape = CodeTheme.shapes.small,
                    label = stringResource(R.string.subtitle_refunded),
                    contentColor = CodeTheme.colors.textSecondary,
                )
            }

            PoolResolution.NotSet -> Unit
        }

        if (resolution is PoolResolution.DecisionMade && resolution !is PoolResolution.Refund) {
            if (didWin) {
                CodeChip(
                    shape = CodeTheme.shapes.small,
                    backgroundColor = CodeTheme.colors.surfaceSuccess,
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_trophy),
                        contentDescription = null,
                        tint = CodeTheme.colors.success,
                    )
                    Text(
                        text = winnings.formatted(
                            formatting = Fiat.Formatting.Truncated
                        ),
                        style = CodeTheme.typography.textSmall,
                        color = CodeTheme.colors.success,
                    )
                }
            }
        }
    }
}