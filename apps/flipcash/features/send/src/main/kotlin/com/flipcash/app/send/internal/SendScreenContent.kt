package com.flipcash.app.send.internal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.core.registry.ScreenRegistry
import com.flipcash.app.core.NavScreenProvider
import com.flipcash.app.core.money.CurrencySelectionKind
import com.flipcash.app.core.ui.AmountWithKeypad
import com.flipcash.features.send.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton

@Composable
internal fun SendScreenContent(viewModel: SendScreenViewModel) {
    val navigator = LocalCodeNavigator.current
    val state by viewModel.stateFlow.collectAsState()
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        AmountWithKeypad(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            amountAnimatedModel = state.amountAnimatedModel,
            currencyFlag = state.currencyModel.selected?.resId,
            prefix = state.currencyModel.selected?.symbol.orEmpty(),
            placeholder = "0",
            hint = if (state.isError) {
                stringResource(R.string.subtitle_giveCashHintLimitExceeded, state.maxAvailableForSend)
            } else {
                stringResource(R.string.subtitle_giveCashHint, state.maxAvailableForSend)
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
            onNumberPressed = { viewModel.dispatchEvent(SendScreenViewModel.Event.OnNumberPressed(it)) },
            onBackspace = { viewModel.dispatchEvent(SendScreenViewModel.Event.OnBackspace) },
            onDecimal = { viewModel.dispatchEvent(SendScreenViewModel.Event.OnDecimalPressed) }
        )

        Box(modifier = Modifier.fillMaxWidth()) {
            CodeButton(
                enabled = state.canSend,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .padding(bottom = CodeTheme.dimens.grid.x2)
                    .navigationBarsPadding(),
                buttonState = ButtonState.Filled,
                isLoading = state.generatingBill.loading,
                isSuccess = state.generatingBill.success,
                text = stringResource(R.string.action_next),
            ) {
                viewModel.dispatchEvent(SendScreenViewModel.Event.OnSend)
            }
        }
    }
}