package com.flipcash.app.currencycreator.internal.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.flipcash.app.core.tokens.CurrencyCreatorResult
import com.flipcash.app.core.tokens.CurrencyCreatorStep
import com.flipcash.app.core.ui.processing.FlowProcessingScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.currencycreator.internal.CurrencyCreatorViewModel
import com.flipcash.core.R
import com.getcode.navigation.flow.flowSharedViewModel
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.opencode.model.financial.Fiat
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.view.LoadingSuccessState
import kotlinx.coroutines.delay

@Composable
internal fun ProcessingScreen() {
    val viewModel = flowSharedViewModel<CurrencyCreatorViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    ProcessingContent(state, viewModel::dispatchEvent)
}

@Composable
internal fun ProcessingContent(
    state: CurrencyCreatorViewModel.State,
    dispatch: (CurrencyCreatorViewModel.Event) -> Unit,
) {
    val flowNavigator = rememberFlowNavigator<CurrencyCreatorStep, CurrencyCreatorResult>()

    FlowProcessingScreen(
        processingState = state.processingState,
        title = { s ->
            Text(
                text = when (s) {
                    LoadingSuccessState.State.Error -> stringResource(R.string.error_title_buySellFailed)
                    LoadingSuccessState.State.Success -> stringResource(R.string.title_currencyIsLive, state.nameFieldState.text.toString())
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
                    LoadingSuccessState.State.Error -> stringResource(R.string.error_description_buySellFailed)
                    LoadingSuccessState.State.Success -> stringResource(R.string.subtitle_currencyIsLive)
                    LoadingSuccessState.State.Loading -> stringResource(R.string.subtitle_processingYourNewCurrencyTransaction)
                    else -> ""
                },
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        },
        bottomBar = {
            if (state.processingState.success) {
                val text = if (state.purchaseAmount == state.totalCost) {
                    stringResource(R.string.action_ok)
                } else {
                    stringResource(
                        R.string.action_receiveNewCurrency,
                        state.purchaseAmount.formatted(rule = Fiat.FormattingRule.Truncated)
                    )
                }

                CodeButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CodeTheme.dimens.inset)
                        .padding(bottom = CodeTheme.dimens.grid.x2)
                        .navigationBarsPadding(),
                    text = text,
                    buttonState = ButtonState.Filled,
                    onClick = {
                        flowNavigator.exitWithResult(CurrencyCreatorResult.Success)
                    },
                )
            } else if (state.processingState.error) {
                CodeButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CodeTheme.dimens.inset)
                        .padding(bottom = CodeTheme.dimens.grid.x2)
                        .navigationBarsPadding(),
                    text = stringResource(R.string.action_ok),
                    buttonState = ButtonState.Filled,
                    onClick = {
                        flowNavigator.exitWithResult(CurrencyCreatorResult.Canceled)
                    },
                )
            }
        },
    )

    BackHandler { /* intercept during processing */ }
}
