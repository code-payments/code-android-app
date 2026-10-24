package com.flipcash.app.balance.internal.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.flipcash.app.core.ui.AppreciationStyle
import com.flipcash.app.core.ui.CurrencyAppreciationLabel
import com.getcode.opencode.compose.LocalExchange
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.text.AmountArea
import com.getcode.ui.theme.CodeCircularProgressIndicator

/**
 * Balance header showing the total portfolio value and an optional appreciation pill.
 *
 * [topPadding] and [bottomPadding] allow callers to override the default symmetric vertical padding
 * (both default to [CodeTheme.dimens.grid.x9], preserving v1 behaviour). The v2 wallet screen uses
 * asymmetric values (~96 dp top, ~44 dp bottom) to match the Figma spec (node 8966:1578).
 *
 * [appreciationStyle] defaults to [AppreciationStyle.Classic] (sentiment-tinted fill); pass
 * [AppreciationStyle.Pill] for the v2 neutral bordered treatment.
 */
@Composable
internal fun BalanceHeader(
    balance: LocalFiat?,
    appreciation: LocalFiat?,
    modifier: Modifier = Modifier,
    topPadding: Dp = CodeTheme.dimens.grid.x9,
    bottomPadding: Dp = CodeTheme.dimens.grid.x9,
    appreciationStyle: AppreciationStyle = AppreciationStyle.Classic,
    onClick: () -> Unit
) {
    val exchange = LocalExchange.current
    Column(
        modifier = modifier
            .padding(horizontal = CodeTheme.dimens.inset)
            .padding(top = topPadding, bottom = bottomPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (balance == null) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CodeCircularProgressIndicator()
            }
        } else {
            AmountArea(
                amountText = balance.nativeAmount.formatted(),
                isAltCaption = false,
                isAltCaptionKinIcon = false,
                currencyResId = exchange.getFlagByCurrency(balance.nativeAmount.currencyCode.name),
                isClickable = true,
                animateDigits = true,
                textStyle = CodeTheme.typography.displayLarge,
                onClick = onClick
            )

            if (appreciation != null) {
                CurrencyAppreciationLabel(
                    appreciation = appreciation.nativeAmount,
                    style = appreciationStyle,
                )
            }
        }
    }
}
