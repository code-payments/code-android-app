package com.flipcash.app.pools.internal.list.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.flipcash.app.core.pools.Pool
import com.flipcash.app.core.pools.PoolResolution
import com.flipcash.features.pools.R
import com.getcode.theme.CodeTheme

@Composable
internal fun PoolSummaryRow(
    pool: Pool,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val didBet = remember(pool) { pool.didBet }
    val isCompleted = remember(pool) { pool.resolution != PoolResolution.NotSet }
    val didWin = remember(pool) { pool.didWin }

    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(
                horizontal = CodeTheme.dimens.grid.x2,
                vertical = CodeTheme.dimens.grid.x2,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = CodeTheme.dimens.grid.x2),
        ) {
            Text(
                text = pool.name,
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.textMain,
            )
            Text(
                text = when {
                    !didBet -> stringResource(R.string.subtitle_notYetBoughtIn, pool.buyIn.formatted())
                    isCompleted && didWin -> stringResource(R.string.subtitle_wonInPool, pool.buyIn.formatted())
                    isCompleted -> stringResource(R.string.subtitle_lostInPool, pool.buyIn.formatted())
                    else -> stringResource(R.string.subtitle_amountInPool, pool.buyIn.formatted())
                },
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
            )
        }

        Icon(
            painter = painterResource(id = R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = CodeTheme.colors.secondary,
        )

    }
}