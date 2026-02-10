package com.flipcash.app.balance.internal.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.flipcash.app.core.ui.CurrencyAppreciationLabel
import com.getcode.opencode.compose.LocalExchange
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.text.AmountArea
import com.getcode.ui.theme.CodeCircularProgressIndicator

@Composable
internal fun BalanceHeader(
    balance: LocalFiat?,
    appreciation: LocalFiat?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val exchange = LocalExchange.current
    Column(
        modifier = modifier
            .padding(horizontal = CodeTheme.dimens.inset)
            .padding(vertical = CodeTheme.dimens.grid.x9),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (balance == null) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CodeCircularProgressIndicator()
            }
        } else {
            key(balance.nativeAmount) {
                Crossfade(balance.nativeAmount) { amount ->
                    AmountArea(
                        amountText = amount.formatted(),
                        isAltCaption = false,
                        isAltCaptionKinIcon = false,
                        currencyResId = exchange.getFlagByCurrency(amount.currencyCode.name),
                        isClickable = true,
                        textStyle = CodeTheme.typography.displayLarge,
                        onClick = onClick
                    )
                }
            }

            if (appreciation != null) {
                Crossfade(appreciation.nativeAmount) { amount ->
                    CurrencyAppreciationLabel(amount)
                }
            }
        }
    }
}