package com.flipcash.app.tokens.internal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.flipcash.app.core.ui.shimmer
import com.flipcash.app.tokens.ui.SwapViewModel
import com.flipcash.features.tokens.R
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.TokenWithBalance
import com.getcode.opencode.model.financial.plus
import com.getcode.theme.CodeTheme
import com.getcode.theme.White05
import com.getcode.theme.bolded
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold

@Composable
internal fun TokenBuyReceiptScreen(viewModel: SwapViewModel) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    TokenBuyReceiptScreen(state, viewModel::dispatchEvent)
}

@Composable
private fun TokenBuyReceiptScreen(
    state: SwapViewModel.State,
    dispatchEvent: (SwapViewModel.Event) -> Unit,
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
                    text = stringResource(R.string.label_buyWarning),
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
                    text = stringResource(R.string.action_buy),
                    buttonState = ButtonState.Filled,
                    isLoading = state.buyProgress.loading,
                    isSuccess = state.buyProgress.success,
                ) {
                    dispatchEvent(SwapViewModel.Event.OnBuyConfirmed)
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
            BuyReceipt(
                fundingToken = state.fundingTokenWithBalance?.token,
                desiredToken = state.tokenWithBalance?.token,
                purchaseAmount = state.confirmedEnteredAmount,
                feeAmount = state.feeAmount,
            )
        }
    }
}

@Composable
private fun BuyReceipt(
    fundingToken: Token?,
    desiredToken: Token?,
    purchaseAmount: Fiat?,
    feeAmount: Fiat,
    modifier: Modifier = Modifier,
) {
    // The funding token and confirmed amount resolve asynchronously after landing here, so
    // either can briefly be null on first composition. Render a shimmer in place of each token
    // row until its data arrives rather than crashing or hiding the row entirely.
    val feeAdjustedPurchaseAmount by remember(purchaseAmount, feeAmount) {
        derivedStateOf {
            purchaseAmount?.let {
                if (!feeAmount.hasDisplayableValue) it else it + feeAmount
            }
        }
    }

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
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.subtitle_youReceive),
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
            )

            if (desiredToken != null && purchaseAmount != null) {
                TokenBalanceRow(
                    tokenWithBalance = TokenWithBalance(
                        token = desiredToken,
                        balance = purchaseAmount
                    ),
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
            } else {
                TokenBalanceRowPlaceholder()
            }
        }

        if (feeAmount.isPositive && purchaseAmount != null) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
            ) {
                ReceiptLineItem(
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.label_amountToBuy),
                    amount = purchaseAmount.formatted()
                )
                ReceiptLineItem(
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.label_exchangeFee),
                    amount = feeAmount.formatted(
                        extraPrefix = if (feeAmount.decimalValue < 0.01) "~" else null,
                    )
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.subtitle_youPay),
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
            )
            if (fundingToken != null && feeAdjustedPurchaseAmount != null) {
                TokenBalanceRow(
                    tokenWithBalance = TokenWithBalance(
                        token = fundingToken,
                        balance = feeAdjustedPurchaseAmount!!
                    ),
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
            } else {
                TokenBalanceRowPlaceholder()
            }
        }
    }
}

/**
 * Shimmer stand-in for a [TokenBalanceRow] shown with a logo + large balance, used while the
 * token or amount is still resolving. Mirrors the row's layout: a circular logo followed by the
 * balance text.
 */
@Composable
private fun TokenBalanceRowPlaceholder(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(CodeTheme.dimens.staticGrid.x6)
                .shimmer(CircleShape)
        )
        Box(
            Modifier
                .width(CodeTheme.dimens.grid.x20)
                .height(CodeTheme.dimens.grid.x6)
                .shimmer()
        )
    }
}