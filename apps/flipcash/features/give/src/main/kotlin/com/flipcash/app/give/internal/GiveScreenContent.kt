package com.flipcash.app.give.internal

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
import com.flipcash.app.core.ui.AmountWithKeypad
import com.flipcash.features.give.R
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton

@Composable
internal fun GiveScreenContent(viewModel: GiveScreenViewModel) {
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
            hint = state.hint,
            onNumberPressed = { viewModel.dispatchEvent(GiveScreenViewModel.Event.OnNumberPressed(it)) },
            onBackspace = { viewModel.dispatchEvent(GiveScreenViewModel.Event.OnBackspace) },
            onDecimal = { viewModel.dispatchEvent(GiveScreenViewModel.Event.OnDecimalPressed) }
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
                viewModel.dispatchEvent(GiveScreenViewModel.Event.OnGive)
            }
        }
    }
}