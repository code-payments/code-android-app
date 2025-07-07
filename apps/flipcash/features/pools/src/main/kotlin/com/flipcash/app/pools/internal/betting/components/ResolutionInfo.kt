package com.flipcash.app.pools.internal.betting.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.flipcash.app.core.pools.PoolResolution
import com.flipcash.app.pools.internal.betting.PoolBettingViewModel
import com.flipcash.shared.payments.R.string
import com.getcode.theme.CodeTheme

@Composable
internal fun ResolutionInfo(state: PoolBettingViewModel.State, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = when {
                state.isHost -> state.isResolved && state.isDistributed == true
                else -> state.isResolved
            },
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = when (state.metadata.resolution) {
                        is PoolResolution.BooleanResolution -> {
                            if (state.metadata.winnerCount > 1) {
                                stringResource(
                                    string.subtitle_winnersReceive,
                                    state.metadata.winningAmount.formatted()
                                )
                            } else {
                                stringResource(
                                    string.subtitle_winnerReceives,
                                    state.metadata.winningAmount.formatted())
                            }
                        }
                        PoolResolution.Refund -> {
                            stringResource(string.subtitle_everyoneGotMoneyBack,)
                        }

                        PoolResolution.NotSet -> ""
                    },
                    style = CodeTheme.typography.textMedium,
                    color = CodeTheme.colors.textSecondary,
                )
                Spacer(Modifier.requiredHeight(CodeTheme.dimens.grid.x6))
            }
        }
    }
}