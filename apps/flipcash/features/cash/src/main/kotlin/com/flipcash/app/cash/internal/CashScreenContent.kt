package com.flipcash.app.cash.internal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.ui.AmountWithKeypad
import com.flipcash.features.cash.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton

@Composable
internal fun GiveScreenContent(viewModel: CashScreenViewModel) {
    val navigator = LocalCodeNavigator.current
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
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
            decimalPlaces = state.currencyModel.fractionUnits,
            hint = if (state.isError) {
                stringResource(
                    R.string.subtitle_giveCashHintLimitExceeded,
                    state.maxAvailableForGive
                )
            } else {
                stringResource(R.string.subtitle_giveCashHint, state.maxAvailableForGive)
            },
            isClickable = true,
            onAmountClicked = {
                navigator.push(AppRoute.Main.RegionSelection)
            },
            isError = state.isError,
            onNumberPressed = { viewModel.dispatchEvent(CashScreenViewModel.Event.OnNumberPressed(it)) },
            onBackspace = { viewModel.dispatchEvent(CashScreenViewModel.Event.OnBackspace) },
            onDecimal = { viewModel.dispatchEvent(CashScreenViewModel.Event.OnDecimalPressed) }
        )

        Box(modifier = Modifier.fillMaxWidth()) {
            CodeButton(
                enabled = state.canGive,
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
                viewModel.dispatchEvent(CashScreenViewModel.Event.OnGive)
            }
        }
    }
}
