package com.flipcash.app.withdrawal.internal.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import com.flipcash.app.theme.FlipcashDesignSystem
import com.getcode.opencode.compose.ExchangeStub
import com.getcode.opencode.compose.LocalExchange
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.minus
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.text.AmountArea
import com.getcode.utils.network.LocalNetworkObserver
import com.getcode.utils.network.NetworkObserverStub

@Composable
internal fun TransactionReceipt(
    amount: LocalFiat,
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
            .background(Color(0xFF071F10), CodeTheme.shapes.medium)
            .padding(
                horizontal = CodeTheme.dimens.grid.x4,
                vertical = CodeTheme.dimens.grid.x8
            )
            .then(modifier),
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x4),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val transferAmount by remember(amount, fee) {
            derivedStateOf {
                (if (fee != null) {
                    val amountAfterFee = amount.usdc - fee
                    if (amountAfterFee.isNegative) {
                        "-${amountAfterFee.formatted()}"
                    } else {
                        amountAfterFee.formatted()
                    }
                } else {
                    amount.usdc.formatted()
                })
            }
        }

        AmountArea(
            amountText = transferAmount,
            isAltCaption = false,
            isAltCaptionKinIcon = false,
            isClickable = false,
            currencyResId = exchange.getFlagByCurrency(amount.usdc.currencyCode.name),
        )

        if (amount.rate.currency != CurrencyCode.USD || fee != null) {
            LineItems(
                amount = amount,
                fee = fee,
                onLearnMoreClicked = onLearnMoreClicked,
            )
        }
    }
}

@Composable
private fun LineItems(
    amount: LocalFiat,
    fee: Fiat?,
    modifier: Modifier = Modifier,
    onLearnMoreClicked: () -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
    ) {
        LineItem(
            modifier = Modifier.fillMaxWidth(),
            label = AnnotatedString("Withdrawal amount"),
            amount = amount.converted.formatted()
        )
        if (amount.rate.currency != CurrencyCode.USD) {
            LineItem(
                modifier = Modifier.fillMaxWidth(),
                label = AnnotatedString("Converted to USDC"),
                amount = amount.usdc.formatted()
            )
        }
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
                        imageVector = Icons.Default.Info,
                        modifier = Modifier.fillMaxSize(),
                        contentDescription = "",
                        colorFilter = ColorFilter.tint(CodeTheme.colors.textSecondary)
                    )
                }
            )
            LineItem(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onLearnMoreClicked),
                label = buildAnnotatedString {
                    append("Less one time fee")
                    append(" ")
                    appendInlineContent("icon")
                },
                amount = "-${fee.formatted()}",
                inlineContentMap = inlineContentMap
            )
        }
    }
}

@Composable
private fun LineItem(
    label: AnnotatedString,
    amount: String,
    modifier: Modifier = Modifier,
    inlineContentMap: Map<String, InlineTextContent> = mapOf(),
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            inlineContent = inlineContentMap,
            style = CodeTheme.typography.textSmall,
            color = CodeTheme.colors.textSecondary,
        )
        Text(
            text = amount,
            style = CodeTheme.typography.textMedium,
            color = CodeTheme.colors.textMain,
        )
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

private val fee = Fiat(0.50, CurrencyCode.USD)

@Preview
@Composable
private fun Preview_CadWithdrawalWithFeeReceipt() {
    FlipcashDesignSystem {
        CompositionLocalProvider(
            LocalNetworkObserver provides NetworkObserverStub(),
            LocalExchange provides ExchangeStub(
                providedRates = rates,
                context = LocalContext.current
            ),
        ) {
            TransactionReceipt(
                amount = LocalFiat(
                    usdc = fiveCad.convertingTo(cadToUsdRate),
                    converted = fiveCad,
                    rate = usdToCadRate
                ),
                fee = fee,
            ) {

            }
        }
    }
}

@Preview
@Composable
private fun Preview_CadWithdrawalWithNoFeeReceipt() {
    FlipcashDesignSystem {
        CompositionLocalProvider(
            LocalNetworkObserver provides NetworkObserverStub(),
            LocalExchange provides ExchangeStub(
                providedRates = rates,
                context = LocalContext.current
            ),
        ) {
            TransactionReceipt(
                amount = LocalFiat(
                    usdc = fiveCad.convertingTo(cadToUsdRate),
                    converted = fiveCad,
                    rate = usdToCadRate
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
    FlipcashDesignSystem {
        CompositionLocalProvider(
            LocalNetworkObserver provides NetworkObserverStub(),
            LocalExchange provides ExchangeStub(
                providedRates = rates,
                context = LocalContext.current
            ),
        ) {
            TransactionReceipt(
                amount = LocalFiat(
                    usdc = fiveUsd,
                    converted = fiveUsd,
                    rate = Rate.oneToOne
                ),
                fee = fee,
            ) {

            }
        }
    }
}

@Preview
@Composable
private fun Preview_UsdWithdrawalWithNoFeeReceipt() {
    FlipcashDesignSystem {
        CompositionLocalProvider(
            LocalNetworkObserver provides NetworkObserverStub(),
            LocalExchange provides ExchangeStub(
                providedRates = rates,
                context = LocalContext.current
            ),
        ) {
            TransactionReceipt(
                amount = LocalFiat(
                    usdc = fiveUsd,
                    converted = fiveUsd,
                    rate = Rate.oneToOne
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
    FlipcashDesignSystem {
        CompositionLocalProvider(
            LocalNetworkObserver provides NetworkObserverStub(),
            LocalExchange provides ExchangeStub(
                providedRates = rates,
                context = LocalContext.current
            ),
        ) {
            TransactionReceipt(
                amount = LocalFiat(
                    usdc = halfDollarCad.convertingTo(cadToUsdRate),
                    converted = halfDollarCad,
                    rate = usdToCadRate
                ),
                fee = fee,
            ) {

            }
        }
    }
}