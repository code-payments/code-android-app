package com.flipcash.app.pools.internal.betting.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.flipcash.app.core.ui.FlagWithFiat
import com.flipcash.features.pools.R
import com.getcode.opencode.model.financial.Fiat
import com.getcode.theme.CodeTheme

@Composable
internal fun PoolTotal(
    poolTotal: Fiat,
    isResolved: Boolean = false,
    isDistributed: Boolean = false,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
    ) {
        FlagWithFiat(
            fiat = poolTotal,
            textStyle = CodeTheme.typography.displayMedium,
            iconSize = CodeTheme.dimens.staticGrid.x5,
        )
        Text(
            text = if (isResolved && isDistributed) {
                stringResource(R.string.subtitle_wasInPool)
            } else {
                stringResource(R.string.subtitle_inPoolSoFar)
            },
            style = CodeTheme.typography.textLarge,
            color = CodeTheme.colors.textSecondary,
        )
    }
}