package com.flipcash.app.withdrawal.internal.confirmation


import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.withdrawal.WithdrawalViewModel
import com.flipcash.app.withdrawal.internal.components.DestinationBox
import com.flipcash.app.withdrawal.internal.components.TransactionReceipt
import com.flipcash.features.withdrawal.R
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.TokenWithBalance
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold

@Composable
internal fun WithdrawalConfirmationScreen(viewModel: WithdrawalViewModel) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    WithdrawalConfirmationScreenContent(state, viewModel::dispatchEvent)
}

@Composable
private fun WithdrawalConfirmationScreenContent(
    state: WithdrawalViewModel.State,
    dispatchEvent: (WithdrawalViewModel.Event) -> Unit
) {
    CodeScaffold(
        bottomBar = {
            CodeButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .padding(bottom = CodeTheme.dimens.grid.x3),
                text = stringResource(R.string.action_withdraw),
                buttonState = ButtonState.Filled,
                enabled = state.withdrawalState.isIdle,
                isLoading = state.withdrawalState.loading,
                isSuccess = state.withdrawalState.success,
            ) {
                dispatchEvent(WithdrawalViewModel.Event.OnWithdraw)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = CodeTheme.dimens.inset)
                .padding(top = CodeTheme.dimens.grid.x8),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(
                CodeTheme.dimens.inset,
            )
        ) {
            TransferInfo(
                tokenWithBalance = TokenWithBalance(
                    state.token!!.token,
                    balance = state.amountEntryState.selectedAmount.nativeAmount,
                ),
                destination = state.destinationState.textFieldState.text.toString(),
                fee = state.destinationState.availability?.feeAmount,
                onLearnMoreClicked = {
                    dispatchEvent(WithdrawalViewModel.Event.OnLearnAboutFee)
                }
            )
        }
    }
}

@Composable
private fun TransferInfo(
    tokenWithBalance: TokenWithBalance,
    fee: Fiat?,
    destination: String,
    onLearnMoreClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.inset)
    ) {
        TransactionReceipt(
            tokenWithBalance = tokenWithBalance,
            fee = fee,
            onLearnMoreClicked = onLearnMoreClicked
        )

        Image(
            imageVector = Icons.Default.ArrowDownward,
            colorFilter = ColorFilter.tint(CodeTheme.colors.border),
            contentDescription = ""
        )

        DestinationBox(destination = destination)
    }
}
