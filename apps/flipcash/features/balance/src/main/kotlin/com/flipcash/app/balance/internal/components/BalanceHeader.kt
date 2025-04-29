package com.flipcash.app.balance.internal.components

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
            .padding(top = CodeTheme.dimens.inset),
    ) {
        if (balance == null) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CodeCircularProgressIndicator()
            }
        } else {
            val isUsd = balance.converted.currencyCode.takeIf {
                it == CurrencyCode.USD
            } != null
            AmountArea(
                amountText = balance.converted.formatted(
                    suffix = balance.converted.currencyCode.takeIf {
                        it != CurrencyCode.USD
                    }?.let {
                        stringResource(R.string.subtitle_ofUsdSuffix)
                    }
                ),
                isAltCaption = false,
                isAltCaptionKinIcon = false,
                captionText = stringResource(R.string.subtitle_balanceIsHeldInUsd).takeIf { !isUsd },
                currencyResId = exchange.getFlagByCurrency(balance.converted.currencyCode.name),
                isClickable = true,
                textStyle = CodeTheme.typography.displayLarge,
                onClick = onClick
            )
        }
    }
}