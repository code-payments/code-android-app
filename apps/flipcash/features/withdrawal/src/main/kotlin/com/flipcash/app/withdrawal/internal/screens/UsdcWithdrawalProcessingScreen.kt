package com.flipcash.app.withdrawal.internal.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.ui.processing.FlowProcessingScreen
import com.flipcash.app.core.withdrawal.WithdrawalResult
import com.flipcash.app.core.withdrawal.WithdrawalStep
import com.flipcash.app.withdrawal.WithdrawalViewModel
import com.flipcash.core.R
import com.getcode.navigation.flow.flowSharedViewModel
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.view.LoadingSuccessState
import kotlin.time.Duration.Companion.minutes

@Composable
internal fun UsdcWithdrawalProcessingScreen() {
    val flowNavigator = rememberFlowNavigator<WithdrawalStep, WithdrawalResult>()
    val viewModel = flowSharedViewModel<WithdrawalViewModel>()

    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        AppBarWithTitle(
            title = stringResource(R.string.title_withdrawing),
            isInModal = true,
            titleAlignment = Alignment.CenterHorizontally,
            onBackIconClicked = { flowNavigator.back() },
        )

        UsdcWithdrawalProcessingContent(state) { flowNavigator.exitWithResult(it) }
    }
}

@Composable
private fun UsdcWithdrawalProcessingContent(state: WithdrawalViewModel.State, onResult: (WithdrawalResult) -> Unit) {
    FlowProcessingScreen(
        processingState = state.withdrawalState,
        processingTime = 3.minutes,
        title = { s ->
            Text(
                text = when (s) {
                    LoadingSuccessState.State.Error -> stringResource(R.string.error_title_failedWithdrawal)
                    LoadingSuccessState.State.Success -> stringResource(R.string.success_title_withdrawalComplete)
                    LoadingSuccessState.State.Loading -> stringResource(R.string.title_processingYourTransaction)
                    else -> ""
                },
                style = CodeTheme.typography.textLarge,
                color = CodeTheme.colors.textMain,
            )
        },
        subtitle = { state ->
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CodeTheme.dimens.grid.x12),
                text = when (state) {
                    LoadingSuccessState.State.Error -> stringResource(R.string.error_description_failedWithdrawal)
                    LoadingSuccessState.State.Success -> stringResource(R.string.success_description_withdrawalComplete)
                    LoadingSuccessState.State.Loading -> stringResource(R.string.subtitle_processingYourTransaction)
                    else -> ""
                },
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        },
        bottomBar = {
            if (state.withdrawalState.success) {
                CodeButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CodeTheme.dimens.inset)
                        .padding(bottom = CodeTheme.dimens.grid.x2)
                        .navigationBarsPadding(),
                    text = stringResource(R.string.action_ok),
                    buttonState = ButtonState.Filled,
                    onClick = {
                        onResult(WithdrawalResult.Success)
                    },
                )
            } else if (state.withdrawalState.error) {
                CodeButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CodeTheme.dimens.inset)
                        .padding(bottom = CodeTheme.dimens.grid.x2)
                        .navigationBarsPadding(),
                    text = stringResource(R.string.action_ok),
                    buttonState = ButtonState.Filled,
                    onClick = {
                        onResult(WithdrawalResult.Canceled)
                    },
                )
            }
        },
    )

    BackHandler { /* intercept during processing */ }
}