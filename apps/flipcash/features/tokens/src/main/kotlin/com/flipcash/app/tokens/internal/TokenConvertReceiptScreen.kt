package com.flipcash.app.tokens.internal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.ui.ReceiptLineItem
import com.flipcash.app.core.ui.TokenBalanceRow
import com.flipcash.app.core.ui.TokenBalanceStyle
import com.flipcash.app.core.ui.rememberTokenBalanceRowStyling
import com.flipcash.app.tokens.ui.SwapViewModel
import com.flipcash.features.tokens.R
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.TokenWithBalance
import com.getcode.opencode.model.financial.plus
import com.getcode.theme.CodeTheme
import com.getcode.theme.White05
import com.getcode.theme.bolded
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold

@Composable
internal fun TokenConvertReceiptScreen(viewModel: SwapViewModel) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    TokenConvertReceiptScreen(state, viewModel::dispatchEvent)
}

@Composable
private fun TokenConvertReceiptScreen(
    state: SwapViewModel.State,
    dispatchEvent: (SwapViewModel.Event) -> Unit,
) {
    val source = state.tokenWithBalance ?: return
    val destination = state.destinationTokenWithBalance ?: return

    // Converting out of Dollars charges the fee on top of the entered amount, so the debit is
    // entered + fee and the user receives the full entered amount. Every other direction takes the
    // fee out of the sale, so the debit is what was entered and the receipt is net of the fee.
    val totalDebited = if (state.isConvertingFromDollars) {
        state.enteredAmount + state.feeAmount
    } else {
        state.enteredAmount
    }

    CodeScaffold(
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CodeTheme.dimens.inset),
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x5),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CodeTheme.dimens.grid.x5),
                    text = stringResource(R.string.label_sellWarning),
                    style = CodeTheme.typography.textSmall,
                    color = CodeTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )

                CodeButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(bottom = CodeTheme.dimens.grid.x3),
                    text = stringResource(R.string.action_confirmConversion),
                    buttonState = ButtonState.Filled,
                    isLoading = state.sellProgress.loading,
                    isSuccess = state.sellProgress.success,
                ) {
                    dispatchEvent(SwapViewModel.Event.OnConvertConfirmed)
                }
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
                alignment = Alignment.CenterVertically
            )
        ) {
            ConvertReceipt(
                source = source,
                destination = destination,
                totalDebited = totalDebited,
                amountToConvert = state.enteredAmount,
                feeAmount = state.feeAmount,
                amountReceived = state.netTransferAmount,
            )
        }
    }
}

@Composable
private fun ConvertReceipt(
    source: TokenWithBalance,
    destination: TokenWithBalance,
    totalDebited: Fiat,
    amountToConvert: Fiat,
    feeAmount: Fiat,
    amountReceived: Fiat,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = CodeTheme.dimens.border,
                color = CodeTheme.colors.border,
                shape = CodeTheme.shapes.medium
            )
            .background(White05, CodeTheme.shapes.medium)
            .padding(
                horizontal = CodeTheme.dimens.grid.x4,
                vertical = CodeTheme.dimens.inset
            )
            .then(modifier),
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x6),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AmountWithCurrency(
            title = stringResource(R.string.subtitle_youConvert),
            tokenWithBalance = source.copy(balance = totalDebited),
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
        ) {
            ReceiptLineItem(
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.label_amountToConvert),
                amount = amountToConvert.formatted(),
            )

            ReceiptLineItem(
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.label_conversionFee),
                amount = feeAmount.formatted(
                    extraPrefix = if (feeAmount.decimalValue < 0.01) "~ " else null,
                ),
            )
        }

        AmountWithCurrency(
            title = stringResource(R.string.subtitle_youReceive),
            tokenWithBalance = destination.copy(balance = amountReceived),
        )
    }
}

/** A titled amount paired with the token it's denominated in — the receipt's two anchor rows. */
@Composable
private fun AmountWithCurrency(
    title: String,
    tokenWithBalance: TokenWithBalance,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
    ) {
        Text(
            text = title,
            style = CodeTheme.typography.textSmall,
            color = CodeTheme.colors.textSecondary,
        )

        // Token logo, not the currency flag: a receipt names the token being moved, and the
        // withdrawal and Get receipts already read that way. Currencies with a shared flag
        // (or none at all) are indistinguishable otherwise.
        TokenBalanceRow(
            tokenWithBalance = tokenWithBalance,
            showName = false,
            showLogo = true,
            showFlag = false,
            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
            styling = rememberTokenBalanceRowStyling(
                balanceDisplayStyle = TokenBalanceStyle.Large(
                    textStyle = CodeTheme.typography.displaySmall.bolded()
                ),
            ),
            contentPadding = PaddingValues(0.dp),
        )

        Text(
            text = tokenWithBalance.displayName,
            style = CodeTheme.typography.textSmall,
            color = CodeTheme.colors.textSecondary,
        )
    }
}
