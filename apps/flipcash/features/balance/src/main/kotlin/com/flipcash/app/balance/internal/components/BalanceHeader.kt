package com.flipcash.app.balance.internal.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.flipcash.features.balance.R
import com.getcode.opencode.compose.LocalExchange
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.text.AmountArea
import com.getcode.ui.theme.CodeCircularProgressIndicator

@Composable
internal fun BalanceHeader(
    balance: LocalFiat?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val exchange = LocalExchange.current
    Column(
        modifier = modifier
            .padding(horizontal = CodeTheme.dimens.inset)
            .padding(vertical = CodeTheme.dimens.inset),
    ) {
        if (balance == null) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CodeCircularProgressIndicator()
            }
        } else {
            Crossfade(balance.converted) { amount ->
                val captionText = if (amount.currencyCode == CurrencyCode.USD) {
                    stringResource(R.string.subtitle_balanceIsHeldInUsdStablecoins)
                } else {
                    balance.usdc.formatted(suffix = stringResource(R.string.subtitle_ofUsdStablecoins))
                }
                AmountArea(
                    amountText = amount.formatted(),
                    isAltCaption = false,
                    isAltCaptionKinIcon = false,
                    captionText = captionText,
                    currencyResId = exchange.getFlagByCurrency(amount.currencyCode.name),
                    isClickable = true,
                    textStyle = CodeTheme.typography.displayLarge,
                    onClick = onClick
                )
            }
        }
    }
}