package com.flipcash.app.payments.internal

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.flipcash.app.core.extensions.toYesOrNo
import com.flipcash.app.core.pools.PoolResolution
import com.flipcash.app.payments.ConfirmationState
import com.flipcash.app.payments.PoolResolutionConfirmation
import com.flipcash.app.theme.FlipcashDesignSystem
import com.flipcash.shared.payments.R
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.toFiat
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.Modal
import com.getcode.ui.components.SlideToConfirm
import com.getcode.ui.core.addIf
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton

@Composable
internal fun PoolResolutionConfirmationModal(
    modifier: Modifier = Modifier,
    confirmation: PoolResolutionConfirmation,
    onSend: () -> Unit,
    onCancel: () -> Unit,
) {
    val state by remember(confirmation.state) {
        derivedStateOf { confirmation.state }
    }

    val isSending by remember(state) {
        derivedStateOf { state is ConfirmationState.Sending }
    }

    val metadata by remember {
        derivedStateOf { confirmation.metadata }
    }

    BackHandler {
        onCancel()
    }

    Modal(modifier) {
        PoolResolutionContent(
            isSending = isSending,
            state = state,
            onApproved = onSend,
            resolution = metadata.resolution,
            winners = metadata.pool.winnerCount,
            buyIn = metadata.pool.buyIn,
            winningAmount = metadata.pool.winningAmountForResolution(metadata.resolution),
            label = stringResource(R.string.action_swipeToConfirm),
        )
        val enabled = state !is ConfirmationState.Sending && state !is ConfirmationState.Sent
        val alpha by animateFloatAsState(targetValue = if (enabled) 1f else 0f, label = "alpha")
        CodeButton(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(alpha),
            enabled = enabled,
            buttonState = ButtonState.Subtle,
            onClick = onCancel,
            text = stringResource(id = android.R.string.cancel),
        )
    }
}

@Composable
private fun PoolResolutionContent(
    isSending: Boolean,
    state: ConfirmationState,
    resolution: PoolResolution.DecisionMade,
    winners: Int,
    winningAmount: Fiat,
    buyIn: Fiat,
    label: String,
    onApproved: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.inset),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(top = CodeTheme.dimens.grid.x4)
                .addIf(resolution is PoolResolution.BooleanResolution) {
                    Modifier.background(
                        color = CodeTheme.colors.success.copy(alpha = 0.69f),
                        shape = CodeTheme.shapes.medium
                    )
                }.addIf(resolution is PoolResolution.Refund) {
                    Modifier.border(
                        width = CodeTheme.dimens.border,
                        color = CodeTheme.colors.border,
                        shape = CodeTheme.shapes.medium
                    ).background(
                        color = CodeTheme.colors.surfaceVariant,
                        shape = CodeTheme.shapes.medium
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.padding(vertical = CodeTheme.dimens.grid.x11),
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = when (resolution) {
                        PoolResolution.Refund -> stringResource(R.string.label_tie)
                        is PoolResolution.BooleanResolution -> resolution.value.toYesOrNo()
                    },
                    style = CodeTheme.typography.displayMedium,
                    color = CodeTheme.colors.textMain,
                )
                Text(
                    text = when (resolution) {
                        is PoolResolution.BooleanResolution -> {
                            if (winners > 1) {
                                stringResource(R.string.subtitle_winnersReceive, winningAmount.formatted())
                            } else {
                                stringResource(R.string.subtitle_winnerReceives, winningAmount.formatted())
                            }
                        }
                        PoolResolution.Refund -> {
                            stringResource(R.string.subtitle_everyoneReceives, buyIn.formatted())
                        }
                    },
                    style = CodeTheme.typography.textSmall,
                    color = CodeTheme.colors.textSecondary,
                )
            }
        }

        Divider(color = CodeTheme.colors.divider)

        SlideToConfirm(
            isLoading = isSending,
            isSuccess = state is ConfirmationState.Sent,
            label = label,
            onConfirm = { onApproved() },
        )
    }
}

@Preview
@Composable
private fun PreviewResolutionModalWithSingleYesWin() {
    FlipcashDesignSystem {
        Box(modifier = Modifier.background(CodeTheme.colors.background)) {
            Modal {
                PoolResolutionContent(
                    isSending = false,
                    state = ConfirmationState.AwaitingConfirmation,
                    onApproved = { },
                    resolution = PoolResolution.BooleanResolution(true),
                    winners = 1,
                    winningAmount = 10.00.toFiat(),
                    buyIn = 10.00.toFiat(),
                    label = stringResource(R.string.action_swipeToConfirm),
                )
                CodeButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(1f),
                    enabled = true,
                    buttonState = ButtonState.Subtle,
                    onClick = { },
                    text = stringResource(id = android.R.string.cancel),
                )
            }
        }
    }
}

@Preview
@Composable
private fun PreviewResolutionModalWithMultipleWin() {
    FlipcashDesignSystem {
        Box(modifier = Modifier.background(CodeTheme.colors.background)) {
            Modal {
                PoolResolutionContent(
                    isSending = false,
                    state = ConfirmationState.AwaitingConfirmation,
                    onApproved = { },
                    resolution = PoolResolution.BooleanResolution(false),
                    winners = 2,
                    winningAmount = 10.00.toFiat(),
                    buyIn = 10.00.toFiat(),
                    label = stringResource(R.string.action_swipeToConfirm),
                )
                CodeButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(1f),
                    enabled = true,
                    buttonState = ButtonState.Subtle,
                    onClick = { },
                    text = stringResource(id = android.R.string.cancel),
                )
            }
        }
    }
}

@Preview
@Composable
private fun PreviewResolutionModalWithTie() {
    FlipcashDesignSystem {
        Box(modifier = Modifier.background(CodeTheme.colors.background)) {
            Modal {
                PoolResolutionContent(
                    isSending = false,
                    state = ConfirmationState.AwaitingConfirmation,
                    onApproved = { },
                    resolution = PoolResolution.Refund,
                    winners = 1,
                    winningAmount = 10.00.toFiat(),
                    buyIn = 10.00.toFiat(),
                    label = stringResource(R.string.action_swipeToConfirm),
                )
                CodeButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(1f),
                    enabled = true,
                    buttonState = ButtonState.Subtle,
                    onClick = { },
                    text = stringResource(id = android.R.string.cancel),
                )
            }
        }
    }
}