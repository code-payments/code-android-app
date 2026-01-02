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
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.ui.ReceiptLineItem
import com.flipcash.app.core.ui.TokenBalanceRow
import com.flipcash.app.core.ui.rememberTokenBalanceRowSizing
import com.flipcash.app.tokens.BuySellSwapTokenViewModel
import com.flipcash.features.tokens.R
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.TokenWithBalance
import com.getcode.opencode.model.financial.minus
import com.getcode.opencode.model.financial.times
import com.getcode.theme.CodeTheme
import com.getcode.theme.bolded
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold
import kotlin.math.roundToInt

@Composable
internal fun TokenSellReceiptScreen(viewModel: BuySellSwapTokenViewModel) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    TokenSellReceiptScreen(state, viewModel::dispatchEvent)
}

@Composable
private fun TokenSellReceiptScreen(
    state: BuySellSwapTokenViewModel.State,
    dispatchEvent: (BuySellSwapTokenViewModel.Event) -> Unit,
) {
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
                    text = stringResource(R.string.action_sell),
                    buttonState = ButtonState.Filled,
                    isLoading = state.sellProgress.loading,
                    isSuccess = state.sellProgress.success,
                ) {
                    dispatchEvent(BuySellSwapTokenViewModel.Event.OnSellConfirmed)
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
            SellReceipt(
                tokenWithBalance = state.tokenWithBalance!!.copy(
                    balance = state.amountEntryState.selectedAmount.nativeAmount
                ),
                fee = state.sellFee,
            )
        }
    }
}

@Composable
private fun SellReceipt(
    tokenWithBalance: TokenWithBalance,
    fee: Double?,
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
            .background(Color(0xFF071F10), CodeTheme.shapes.medium)
            .padding(
                horizontal = CodeTheme.dimens.grid.x4,
                vertical = CodeTheme.dimens.inset
            )
            .then(modifier),
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x6),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val feeAmount by remember(fee, tokenWithBalance.balance) {
            derivedStateOf {
                if (fee == null) return@derivedStateOf Fiat.Zero
                tokenWithBalance.balance * (fee / 100.0)
            }
        }

        val netTransferAmount by remember(tokenWithBalance.balance, feeAmount) {
            derivedStateOf {
                tokenWithBalance.balance - feeAmount
            }
        }

        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
        ) {
            ReceiptLineItem(
                modifier = Modifier.fillMaxWidth(),
                label = "Sell amount",
                amount = tokenWithBalance.balance.formatted(),
            )

            if (fee != null) {
                ReceiptLineItem(
                    modifier = Modifier.fillMaxWidth(),
                    label = "${fee.roundToInt()}% Fee",
                    amount = feeAmount.formatted(),
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.subtitle_youReceive),
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
            )

            TokenBalanceRow(
                tokenWithBalance = tokenWithBalance.copy(balance = netTransferAmount),
                showName = false,
                showLogo = false,
                showFlag = true,
                horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
                sizing = rememberTokenBalanceRowSizing(
                    balanceTextStyle = CodeTheme.typography.displaySmall.bolded(),
                    flagSize = CodeTheme.dimens.grid.x4,
                ),
                contentPadding = PaddingValues(0.dp),
            )
        }
    }
}