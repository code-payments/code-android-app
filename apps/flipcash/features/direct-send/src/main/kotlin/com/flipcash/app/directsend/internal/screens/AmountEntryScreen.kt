package com.flipcash.app.directsend.internal.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.tokens.TokenPurpose
import com.flipcash.app.core.ui.AmountWithKeypad
import com.flipcash.app.core.ui.ConfirmationStyle
import com.flipcash.app.core.ui.TokenSelectionPill
import com.flipcash.app.directsend.internal.AmountEntryViewModel
import com.flipcash.features.directsend.R
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Token
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.components.SlideToConfirm
import com.getcode.ui.theme.CodeButton
import com.getcode.view.LoadingSuccessState
import kotlinx.coroutines.flow.filterIsInstance

internal sealed interface AmountEntryResult {
    data object Cancelled : AmountEntryResult
    data class Confirmed(val amount: Fiat, val token: Token) : AmountEntryResult
}

@Composable
internal fun AmountEntryScreen(
    title: @Composable (Token?) -> Unit,
    canChangeCurrency: Boolean = false,
    navigator: CodeNavigator = LocalCodeNavigator.current,
    confirmationStyle: ConfirmationStyle = ConfirmationStyle.Button,
    confirmationState: LoadingSuccessState = LoadingSuccessState(),
    onResult: (AmountEntryResult) -> Unit,
    leftContents: @Composable () -> Unit = {
        AppBarDefaults.UpNavigation { onResult(AmountEntryResult.Cancelled) }
    },
    rightContents: @Composable () -> Unit = { }
) {
    val viewModel = hiltViewModel<AmountEntryViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.eventFlow
            .filterIsInstance<AmountEntryViewModel.Event.AmountConfirmed>()
            .collect { event -> onResult(AmountEntryResult.Confirmed(event.amount, event.token)) }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppBarWithTitle(
            isInModal = true,
            title = { title(state.token) },
            leftIcon = leftContents,
            rightContents = rightContents
        )

        AmountWithKeypad(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            amountAnimatedModel = state.amountAnimatedModel,
            currencyFlag = state.currencyModel.selected?.resId,
            prefix = state.currencyModel.selected?.symbol.orEmpty(),
            placeholder = "0",
            decimalPlaces = state.currencyModel.fractionUnits,
            isError = state.isError,
            isClickable = canChangeCurrency,
            onAmountClicked = {
                navigator.push(AppRoute.Main.RegionSelection)
            },
            hint = when {
                state.maxSendFormatted.isEmpty() -> ""
                state.isError -> stringResource(
                    R.string.subtitle_sendHintLimitExceeded,
                    state.maxSendFormatted
                )
                else -> stringResource(R.string.subtitle_sendHint, state.maxSendFormatted)
            },
            onNumberPressed = {
                viewModel.dispatchEvent(AmountEntryViewModel.Event.OnNumberPressed(it))
            },
            onBackspace = {
                viewModel.dispatchEvent(AmountEntryViewModel.Event.OnBackspace)
            },
            onDecimal = {
                viewModel.dispatchEvent(AmountEntryViewModel.Event.OnDecimalPressed)
            },
        )

        Box(modifier = Modifier.fillMaxWidth()) {
            val enabled = state.canSend && confirmationState.isIdle
            when (confirmationStyle) {
                ConfirmationStyle.Button -> {
                    CodeButton(
                        enabled = enabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CodeTheme.dimens.inset)
                            .padding(bottom = CodeTheme.dimens.grid.x2)
                            .navigationBarsPadding(),
                        buttonState = ButtonState.Filled,
                        isLoading = confirmationState.loading,
                        isSuccess = confirmationState.success,
                        text = stringResource(R.string.action_send),
                    ) {
                        viewModel.dispatchEvent(AmountEntryViewModel.Event.OnConfirmRequested)
                    }
                }
                ConfirmationStyle.Slide -> {
                    SlideToConfirm(
                        enabled = enabled,
                        onConfirm = {
                            viewModel.dispatchEvent(AmountEntryViewModel.Event.OnConfirmRequested)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CodeTheme.dimens.inset)
                            .padding(bottom = CodeTheme.dimens.grid.x2)
                            .navigationBarsPadding(),
                        isLoading = confirmationState.loading,
                        isSuccess = confirmationState.success,
                        label = stringResource(R.string.action_swipeToSend),
                    )
                }
            }
        }
    }
}
