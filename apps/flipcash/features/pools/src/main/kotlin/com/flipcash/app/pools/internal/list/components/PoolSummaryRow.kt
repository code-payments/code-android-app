package com.flipcash.app.pools.internal.list.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.tooling.preview.Preview
import com.flipcash.app.core.pools.Pool
import com.flipcash.app.core.pools.PoolBet
import com.flipcash.app.core.pools.PoolBetOutcome
import com.flipcash.app.core.pools.PoolResolution
import com.flipcash.app.core.pools.PoolWithBets
import com.flipcash.app.theme.FlipcashDesignSystem
import com.flipcash.features.pools.R
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.toFiat
import com.getcode.solana.keys.PublicKey
import com.getcode.theme.CodeTheme
import com.getcode.utils.decodeBase58
import kotlinx.datetime.Clock

@Composable
internal fun PoolSummaryRow(
    poolWithBets: PoolWithBets,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    PoolSummaryRow(
        pool = poolWithBets.pool,
        isHost = poolWithBets.isHost,
        winningAmount = poolWithBets.winningAmount,
        totalPoolAmount = poolWithBets.totalPoolAmount,
        modifier = modifier,
        onClick = onClick,
    )
}

@Composable
internal fun PoolSummaryRow(
    pool: Pool,
    isHost: Boolean,
    winningAmount: Fiat,
    totalPoolAmount: Fiat,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val isCompleted = remember(pool) { pool.resolution != PoolResolution.NotSet }
    val didWin = remember(pool) { pool.didWin }

    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(
                horizontal = CodeTheme.dimens.grid.x2,
                vertical = CodeTheme.dimens.grid.x3,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = CodeTheme.dimens.grid.x2),
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
        ) {
            if (isHost) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_host),
                        contentDescription = null,
                        tint = CodeTheme.colors.textSecondary,
                    )

                    Text(
                        text = stringResource(R.string.subtitle_host),
                        style = CodeTheme.typography.textSmall,
                        color = CodeTheme.colors.textSecondary,
                    )
                }
            }
            Text(
                text = pool.name,
                style = CodeTheme.typography.textLarge,
                color = CodeTheme.colors.textMain,
            )

            if (isCompleted) {
                CompletedPoolStatusRow(
                    winnings = winningAmount,
                    buyIn = pool.buyIn,
                    didWin = didWin,
                    resolution = pool.resolution,
                )
            } else {
                Text(
                    text = stringResource(
                        R.string.subtitle_totalInPool,
                        totalPoolAmount.formatted(
                            formatting = Fiat.Formatting.Truncated
                        )
                    ),
                    style = CodeTheme.typography.textSmall,
                    color = CodeTheme.colors.textSecondary,
                )
            }
        }

        Icon(
            painter = painterResource(id = R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = CodeTheme.colors.secondary,
        )
    }
}

private val pool = Pool(
    id = "3GHjGey5F3fVProC3mYpiBpi7dCegFNz3wYtHSTiQnPt".decodeBase58().toList(),
    creator = "3GHjGey5F3fVProC3mYpiBpi7dCegFNz3wYtHSTiQnPt".decodeBase58().toList(),
    isOpen = true,
    buyIn = 5.00.toFiat(),
    fundingDestination = PublicKey.fromBase58("7XbL9kZ3mPqW8nR2vY5tJ6hQ4uF1cA9xN3gT8rK5pM"),
    name = "Will Flipcash Pools launch before the end of June?",
    createdAt = Clock.System.now(),
    closedAt = null,
    didWin = false,
    didBet = false,
)

val bets = listOf(
    PoolBet(
        id = "3GHjGey5F3fVProC3mYpiBpi7dCegFNz3wYtHSTiQnPt".decodeBase58().toList(),
        userId = "3GHjGey5F3fVProC3mYpiBpi7dCegFNz3wYtHSTiQnPt".decodeBase58().toList(),
        selectedOutcome = PoolBetOutcome.BooleanOutcome(false),
        payoutDestination = PublicKey.fromBase58("7XbL9kZ3mPqW8nR2vY5tJ6hQ4uF1cA9xN3gT8rK5pM"),
        placedAt = Clock.System.now(),
    )
)

private val refundedPool = pool.copy(resolution = PoolResolution.Refund)

private val wonPool = pool.copy(didWin = true, resolution = PoolResolution.BooleanResolution(false))
private val lostPool = pool.copy(didWin = false, resolution = PoolResolution.BooleanResolution(true))

@Preview
@Composable
private fun OpenHostedNoBetsPreview() {
    FlipcashDesignSystem {
        Box(modifier = Modifier.background(CodeTheme.colors.background)) {
            PoolSummaryRow(
                pool = pool,
                isHost = true,
                winningAmount = 0.00.toFiat(),
                totalPoolAmount = 0.00.toFiat(),
                onClick = {},
            )
        }
    }
}

@Preview
@Composable
private fun OpenNoBetsPreview() {
    FlipcashDesignSystem {
        Box(modifier = Modifier.background(CodeTheme.colors.background)) {
            PoolSummaryRow(
                pool = pool,
                isHost = false,
                winningAmount = 0.00.toFiat(),
                totalPoolAmount = 0.00.toFiat(),
                onClick = {},
            )
        }
    }
}

@Preview
@Composable
private fun OpenHostedHasBetsPreview() {
    FlipcashDesignSystem {
        Box(modifier = Modifier.background(CodeTheme.colors.background)) {
            PoolSummaryRow(
                pool = pool,
                isHost = true,
                winningAmount = 0.00.toFiat(),
                totalPoolAmount = 10.00.toFiat(),
                onClick = {},
            )
        }
    }
}

@Preview
@Composable
private fun OpenHasBetsPreview() {
    FlipcashDesignSystem {
        Box(modifier = Modifier.background(CodeTheme.colors.background)) {
            PoolSummaryRow(
                pool = pool,
                isHost = false,
                winningAmount = 0.00.toFiat(),
                totalPoolAmount = 10.00.toFiat(),
                onClick = {},
            )
        }
    }
}

@Preview
@Composable
private fun ClosedHostedRefundedPreview() {
    FlipcashDesignSystem {
        Box(modifier = Modifier.background(CodeTheme.colors.background)) {
            PoolSummaryRow(
                pool = refundedPool,
                isHost = true,
                winningAmount = 0.00.toFiat(),
                totalPoolAmount = 10.00.toFiat(),
                onClick = {},
            )
        }
    }
}

@Preview
@Composable
private fun ClosedRefundedPreview() {
    FlipcashDesignSystem {
        Box(modifier = Modifier.background(CodeTheme.colors.background)) {
            PoolSummaryRow(
                pool = refundedPool,
                isHost = false,
                winningAmount = 0.00.toFiat(),
                totalPoolAmount = 10.00.toFiat(),
                onClick = {},
            )
        }
    }
}

@Preview
@Composable
private fun ClosedHostedWonPreview() {
    FlipcashDesignSystem {
        Box(modifier = Modifier.background(CodeTheme.colors.background)) {
            PoolSummaryRow(
                pool = wonPool,
                isHost = true,
                winningAmount = 10.00.toFiat(),
                totalPoolAmount = 10.00.toFiat(),
                onClick = {},
            )
        }
    }
}

@Preview
@Composable
private fun ClosedWonPreview() {
    FlipcashDesignSystem {
        Box(modifier = Modifier.background(CodeTheme.colors.background)) {
            PoolSummaryRow(
                pool = wonPool,
                isHost = false,
                winningAmount = 10.00.toFiat(),
                totalPoolAmount = 10.00.toFiat(),
                onClick = {},
            )
        }
    }
}

@Preview
@Composable
private fun ClosedHostedLostPreview() {
    FlipcashDesignSystem {
        Box(modifier = Modifier.background(CodeTheme.colors.background)) {
            PoolSummaryRow(
                pool = lostPool,
                isHost = true,
                winningAmount = 0.00.toFiat(),
                totalPoolAmount = 10.00.toFiat(),
                onClick = {},
            )
        }
    }
}

@Preview
@Composable
private fun ClosedLostPreview() {
    FlipcashDesignSystem {
        Box(modifier = Modifier.background(CodeTheme.colors.background)) {
            PoolSummaryRow(
                pool = lostPool,
                isHost = false,
                winningAmount = 0.00.toFiat(),
                totalPoolAmount = 10.00.toFiat(),
                onClick = {},
            )
        }
    }
}