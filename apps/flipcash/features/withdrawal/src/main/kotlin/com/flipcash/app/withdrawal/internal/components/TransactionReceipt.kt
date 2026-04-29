package com.flipcash.app.withdrawal.internal.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.flipcash.app.core.ui.ReceiptLineItem
import com.flipcash.app.core.ui.TokenBalanceRow
import com.flipcash.app.core.ui.rememberTokenBalanceRowSizing
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.features.withdrawal.R
import com.getcode.opencode.compose.ExchangeStub
import com.getcode.opencode.compose.LocalExchange
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.TokenWithBalance
import com.getcode.opencode.model.financial.minus
import com.getcode.opencode.model.financial.toFiat
import com.getcode.opencode.model.financial.usdf
import com.getcode.solana.keys.Mint
import com.getcode.theme.CodeTheme
import com.getcode.theme.White05
import com.getcode.theme.bolded
import com.getcode.utils.network.LocalNetworkObserver
import com.getcode.utils.network.NetworkObserverStub

@Composable
internal fun TransactionReceipt(
    tokenWithBalance: TokenWithBalance,
    fee: Fiat?,
    modifier: Modifier = Modifier,
    onLearnMoreClicked: () -> Unit
) {
    val exchange = LocalExchange.current
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
                vertical = CodeTheme.dimens.grid.x8
            )
            .then(modifier),
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x4),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val netTransferAmount by remember(tokenWithBalance, fee) {
            derivedStateOf {
                tokenWithBalance.balance - (fee ?: 0.toFiat(tokenWithBalance.balance.currencyCode))
            }
        }

        LineItems(
            tokenWithBalance = tokenWithBalance,
            transferAmount = netTransferAmount,
            fee = fee,
            onLearnMoreClicked = onLearnMoreClicked,
        )

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
                horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
                iconOverride = { icon ->
                    if (tokenWithBalance.token.address == Mint.usdf) {
                        painterResource(R.drawable.ic_usdc_on_solana)
                    } else {
                        icon
                    }
                },
                formattedBalance = { amount ->
                    amount.convertingToUsdIfNeeded(exchange.entryRate)
                        .estimatedTokenAmountIn(tokenWithBalance.token, fractionDigits = 2)
                },
                sizing = rememberTokenBalanceRowSizing(
                    balanceTextStyle = CodeTheme.typography.displayMedium.bolded(),
                ),
                contentPadding = PaddingValues(0.dp),
            )
        }
    }
}

@Composable
private fun LineItems(
    tokenWithBalance: TokenWithBalance,
    transferAmount: Fiat,
    fee: Fiat?,
    modifier: Modifier = Modifier,
    onLearnMoreClicked: () -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
    ) {
        ReceiptLineItem(
            modifier = Modifier.fillMaxWidth(),
            label = AnnotatedString("Withdrawal amount"),
            amount = tokenWithBalance.balance.formatted()
        )

        if (fee != null) {
            val inlineContentMap = mapOf(
                "icon" to InlineTextContent(
                    Placeholder(
                        width = CodeTheme.typography.textSmall.fontSize,
                        height = CodeTheme.typography.textSmall.fontSize,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                    )
                ) {
                    Image(
                        imageVector = Icons.Outlined.Info,
                        modifier = Modifier.fillMaxSize(),
                        contentDescription = "",
                        colorFilter = ColorFilter.tint(CodeTheme.colors.textSecondary)
                    )
                }
            )
            ReceiptLineItem(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onLearnMoreClicked),
                label = buildAnnotatedString {
                    withStyle(style = SpanStyle(textDecoration = TextDecoration.Underline)) {
                        append("Less fee")
                    }
                    append(" ")
                    appendInlineContent("icon")
                },
                amount = "-${fee.formatted()}",
                inlineContentMap = inlineContentMap
            )

            val netAmount = remember(transferAmount) {
                if (transferAmount.isNegative) {
                    "-${transferAmount.formatted()}"
                } else {
                    transferAmount.formatted()
                }
            }

            ReceiptLineItem(
                modifier = Modifier.fillMaxWidth(),
                label = AnnotatedString("Net amount"),
                amount = netAmount
            )
        }

        if (tokenWithBalance.token.address != Mint.usdf) {
            val exchange = LocalExchange.current
            ReceiptLineItem(
                modifier = Modifier.fillMaxWidth(),
                label = AnnotatedString("Amount in ${tokenWithBalance.displayName}"),
                amount = transferAmount.convertingToUsdIfNeeded(exchange.entryRate)
                    .estimatedTokenAmountIn(tokenWithBalance.token, fractionDigits = 2),
            )
        }
    }
}

private val fiveUsd = Fiat(5.00, CurrencyCode.USD)
private val fiveCad = Fiat(5.00, CurrencyCode.CAD)
private val halfDollarCad = Fiat(0.50, CurrencyCode.CAD)
private val usdToCadRate = Rate(fx = 1.37161, currency = CurrencyCode.CAD)
private val cadToUsdRate = Rate(fx = 0.72894, currency = CurrencyCode.USD)
private val rates = mapOf(
    CurrencyCode.CAD to usdToCadRate,
    CurrencyCode.USD to cadToUsdRate,
)

private val feeUsd = Fiat(0.50, CurrencyCode.USD)
private val feeCad = feeUsd.convertingTo(usdToCadRate)

@Preview
@Composable
private fun Preview_CadWithdrawalWithFeeReceipt() {
    FlipcashPreview {
        CompositionLocalProvider(
            LocalNetworkObserver provides NetworkObserverStub(),
            LocalExchange provides ExchangeStub(
                providedRates = rates,
                context = LocalContext.current
            ),
        ) {
            TransactionReceipt(
                tokenWithBalance = TokenWithBalance(
                    token = Token.usdf,
                    balance = fiveCad
                ),
                fee = feeCad,
            ) {

            }
        }
    }
}

@Preview
@Composable
private fun Preview_CadWithdrawalWithNoFeeReceipt() {
    FlipcashPreview {
        CompositionLocalProvider(
            LocalNetworkObserver provides NetworkObserverStub(),
            LocalExchange provides ExchangeStub(
                providedRates = rates,
                context = LocalContext.current
            ),
        ) {
            TransactionReceipt(
                tokenWithBalance = TokenWithBalance(
                    token = Token.usdf,
                    balance = fiveCad
                ),
                fee = null,
            ) {

            }
        }
    }
}

@Preview
@Composable
private fun Preview_UsdWithdrawalWithFeeReceipt() {
    FlipcashPreview {
        CompositionLocalProvider(
            LocalNetworkObserver provides NetworkObserverStub(),
            LocalExchange provides ExchangeStub(
                providedRates = rates,
                context = LocalContext.current
            ),
        ) {
            TransactionReceipt(
                tokenWithBalance = TokenWithBalance(
                    token = Token.usdf,
                    balance = fiveUsd
                ),
                fee = feeUsd,
            ) {

            }
        }
    }
}

@Preview
@Composable
private fun Preview_UsdWithdrawalWithNoFeeReceipt() {
    FlipcashPreview {
        CompositionLocalProvider(
            LocalNetworkObserver provides NetworkObserverStub(),
            LocalExchange provides ExchangeStub(
                providedRates = rates,
                context = LocalContext.current
            ),
        ) {
            TransactionReceipt(
                tokenWithBalance = TokenWithBalance(
                    token = Token.usdf,
                    balance = fiveUsd
                ),
                fee = null,
            ) {

            }
        }
    }
}

@Preview
@Composable
private fun Preview_CadWithdrawalWithFeeButTooSmallReceipt() {
    FlipcashPreview {
        CompositionLocalProvider(
            LocalNetworkObserver provides NetworkObserverStub(),
            LocalExchange provides ExchangeStub(
                providedRates = rates,
                context = LocalContext.current
            ),
        ) {
            TransactionReceipt(
                tokenWithBalance = TokenWithBalance(
                    token = Token.usdf,
                    balance = halfDollarCad
                ),
                fee = feeCad,
            ) {

            }
        }
    }
}