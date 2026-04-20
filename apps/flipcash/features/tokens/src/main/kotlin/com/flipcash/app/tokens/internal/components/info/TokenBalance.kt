package com.flipcash.app.tokens.internal.components.info

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flipcash.app.core.ui.CurrencyAppreciationLabel
import com.getcode.opencode.compose.LocalExchange
import com.getcode.opencode.model.financial.Fiat
import com.getcode.theme.CodeTheme
import com.getcode.theme.bolded
import com.getcode.theme.extraSmall
import com.getcode.ui.components.CodeChip
import com.getcode.ui.components.text.AmountArea
import com.getcode.ui.theme.CodeCircularProgressIndicator

@Composable
internal fun TokenBalance(
    modifier: Modifier = Modifier,
    balance: Fiat?,
    appreciation: Fiat?,
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
            key(balance) {
                AmountArea(
                    amountText = balance.formatted(),
                    isAltCaption = false,
                    isAltCaptionKinIcon = false,
                    captionText = null,
                    currencyResId = exchange.getFlagByCurrency(balance.currencyCode.name),
                    isClickable = true,
                    animateDigits = true,
                    textStyle = CodeTheme.typography.displayLarge,
                    onClick = onClick
                )
            }

            if (appreciation != null) {
                key(appreciation) {
                    CurrencyAppreciationLabel(appreciation)
                }
            }
        }
    }
}