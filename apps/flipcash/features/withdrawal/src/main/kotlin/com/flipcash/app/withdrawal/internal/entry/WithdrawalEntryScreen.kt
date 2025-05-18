package com.flipcash.app.withdrawal.internal.entry

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.registry.ScreenRegistry
import com.flipcash.app.core.NavScreenProvider
import com.flipcash.app.core.money.CurrencySelectionKind
import com.flipcash.app.core.ui.AmountWithKeypad
import com.flipcash.app.withdrawal.WithdrawalViewModel
import com.flipcash.features.withdrawal.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Composable
internal fun WithdrawalEntryScreen(viewModel: WithdrawalViewModel) {
    val navigator = LocalCodeNavigator.current
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    WithdrawalEntryScreenContent(state, viewModel::dispatchEvent)

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<WithdrawalViewModel.Event.OnAmountConfirmed>()
            .onEach {
                navigator.push(
                    ScreenRegistry.get(
                        NavScreenProvider.HomeScreen.Menu.Withdrawal.Destination
                    )
                )
            }.launchIn(this)
    }
}

@Composable
private fun WithdrawalEntryScreenContent(
    state: WithdrawalViewModel.State,
    dispatchEvent: (WithdrawalViewModel.Event) -> Unit
) {
    val navigator = LocalCodeNavigator.current

    val entryState = remember(state.amountEntryState) {
        state.amountEntryState
    }

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        AmountWithKeypad(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            amountAnimatedModel = entryState.amountAnimatedModel,
            currencyFlag = entryState.currencyModel.selected?.resId,
            prefix = entryState.currencyModel.selected?.symbol.orEmpty(),
            placeholder = "0",
            hint = if (state.isError) {
                stringResource(R.string.subtitle_giveCashHintLimitExceeded, state.balance.converted.formatted())
            } else {
                stringResource(R.string.subtitle_giveCashHint, state.balance.converted.formatted())
            },
            isClickable = true,
            onAmountClicked = {
                navigator.push(
                    ScreenRegistry.get(
                        NavScreenProvider.HomeScreen.CurrencySelection(
                            kind = CurrencySelectionKind.Entry
                        )
                    )
                )
            },
            isError = state.isError,
            onNumberPressed = { dispatchEvent(WithdrawalViewModel.Event.OnNumberPressed(it)) },
            onBackspace = { dispatchEvent(WithdrawalViewModel.Event.OnBackspace) },
            onDecimal = { dispatchEvent(WithdrawalViewModel.Event.OnDecimalPressed) }
        )

        Box(modifier = Modifier.fillMaxWidth()) {
            CodeButton(
                enabled = state.canWithdraw,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .padding(bottom = CodeTheme.dimens.grid.x2)
                    .navigationBarsPadding(),
                buttonState = ButtonState.Filled,
                isLoading = entryState.confirmingAmount.loading,
                isSuccess = entryState.confirmingAmount.success,
                text = stringResource(R.string.action_next),
            ) {
                dispatchEvent(WithdrawalViewModel.Event.OnAmountConfirmed)
            }
        }
    }
}