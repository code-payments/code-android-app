package com.flipcash.app.tokens.internal

import android.Manifest
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.tokens.FundingSource
import com.flipcash.app.core.tokens.SwapPurpose
import com.flipcash.app.core.ui.buildNotifyButtonLabel
import com.flipcash.app.core.ui.processing.FlowProcessingScreen
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.app.tokens.ui.SwapViewModel
import com.flipcash.core.R
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.util.permissions.ProvideTestPermissions
import com.getcode.util.permissions.rememberNotificationPermission
import com.getcode.view.LoadingSuccessState

@Composable
internal fun TokenTxProcessingScreen(
    viewModel: SwapViewModel,
    processingProgressOverride: LoadingSuccessState? = null,
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val effectiveState = if (processingProgressOverride != null) {
        state.copy(processingProgress = processingProgressOverride)
    } else {
        state
    }
    TokenTxProcessingScreen(state = effectiveState, dispatch = viewModel::dispatchEvent)
}

@Composable
private fun TokenTxProcessingScreen(
    state: SwapViewModel.State,
    dispatch: (SwapViewModel.Event) -> Unit,
) {
    val notifications = rememberNotificationPermission()

    FlowProcessingScreen(
        processingState = state.processingProgress,
        topBar = {
            AppBarWithTitle(
                isInModal = true,
                title = when (val purpose = state.purpose) {
                    is SwapPurpose.Buy if purpose.fundingSource != FundingSource.Flexible ->
                        stringResource(R.string.title_depositingToken, state.tokenName)

                    is SwapPurpose.BalanceIncrease -> stringResource(
                        R.string.title_purchasingToken,
                        state.tokenName
                    )

                    is SwapPurpose.BalanceDecrease -> stringResource(
                        R.string.title_sellingToken,
                        state.tokenName
                    )

                    else -> ""
                },
                titleAlignment = Alignment.CenterHorizontally,
            )
        },
        bottomBar = {
            when (state.processingProgress.state) {
                LoadingSuccessState.State.Idle -> Unit
                LoadingSuccessState.State.Loading -> {
                    val notifyLabel = buildNotifyButtonLabel()
                    CodeButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CodeTheme.dimens.inset)
                            .padding(bottom = CodeTheme.dimens.grid.x2)
                            .navigationBarsPadding(),
                        text = if (notifications.isGranted) {
                            notifyLabel.first
                        } else {
                            AnnotatedString(stringResource(R.string.action_notifyMeWhenComplete))
                        },
                        inlineContent = if (notifications.isGranted) {
                            notifyLabel.second
                        } else {
                            emptyMap()
                        },
                        buttonState = ButtonState.Filled,
                        enabled = !notifications.isGranted,
                        onClick = {
                            notifications.launch()
                        }
                    )
                }
                LoadingSuccessState.State.Success,
                LoadingSuccessState.State.Error -> {
                    CodeButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CodeTheme.dimens.inset)
                            .padding(bottom = CodeTheme.dimens.grid.x2)
                            .navigationBarsPadding(),
                        text = stringResource(R.string.action_ok),
                        buttonState = ButtonState.Filled,
                        onClick = {
                            dispatch(SwapViewModel.Event.OnTransactionSuccessful)
                        }
                    )
                }
            }
        },
        title = { processingStateValue ->
            Text(
                text = when (processingStateValue) {
                    LoadingSuccessState.State.Error -> stringResource(R.string.error_title_buySellFailed)
                    LoadingSuccessState.State.Idle -> ""
                    LoadingSuccessState.State.Loading -> stringResource(R.string.title_processingYourTransaction)
                    LoadingSuccessState.State.Success -> {
                        val name = when (state.purpose) {
                            is SwapPurpose.BalanceIncrease -> state.tokenName
                            else -> stringResource(R.string.title_cashReserves)
                        }
                        state.netTransferAmount.formatted(suffix = stringResource(R.string.label_ofToken, name))
                    }
                },
                style = CodeTheme.typography.textLarge,
                color = CodeTheme.colors.textMain,
            )
        },
        subtitle = { processingStateValue ->
            Text(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = CodeTheme.dimens.grid.x12),
                text = when (processingStateValue) {
                    LoadingSuccessState.State.Error -> stringResource(R.string.error_description_buySellFailed)
                    LoadingSuccessState.State.Idle -> ""
                    LoadingSuccessState.State.Loading -> stringResource(R.string.subtitle_processingYourTransaction)
                    LoadingSuccessState.State.Success -> stringResource(R.string.subtitle_wasAddedToYourWallet)
                },
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        },
    )
}

@Preview
@Composable
private fun TxProcessiongPreview() {
    FlipcashPreview {
        ProvideTestPermissions(granted = setOf(Manifest.permission.POST_NOTIFICATIONS)) {
            TokenTxProcessingScreen(
                state = SwapViewModel.State(
                    processingProgress = LoadingSuccessState(loading = true)
                )
            ) { }
        }
    }
}
