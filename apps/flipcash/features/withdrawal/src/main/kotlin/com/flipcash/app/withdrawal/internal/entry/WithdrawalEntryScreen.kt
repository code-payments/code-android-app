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
import com.flipcash.app.core.ui.AmountWithKeypad
import com.flipcash.app.withdrawal.WithdrawalViewModel
import com.flipcash.features.withdrawal.R
import com.getcode.solana.keys.Mint
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton

@Composable
internal fun WithdrawalEntryScreen(
    viewModel: WithdrawalViewModel,
    mint: Mint,
    onOpenRegionSelection: () -> Unit,
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    WithdrawalEntryScreenContent(state, viewModel::dispatchEvent, onOpenRegionSelection)

    LaunchedEffect(viewModel) {
        viewModel.dispatchEvent(WithdrawalViewModel.Event.OnMintSelected(mint))
    }
}

@Composable
private fun WithdrawalEntryScreenContent(
    state: WithdrawalViewModel.State,
    dispatchEvent: (WithdrawalViewModel.Event) -> Unit,
    onOpenRegionSelection: () -> Unit,
) {
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
                stringResource(R.string.subtitle_giveCashHintLimitExceeded, state.tokenBalance.formatted())
            } else {
                stringResource(R.string.subtitle_giveCashHint, state.tokenBalance.formatted())
            },
            decimalPlaces = entryState.currencyModel.fractionUnits,
            isClickable = true,
            onAmountClicked = onOpenRegionSelection,
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
