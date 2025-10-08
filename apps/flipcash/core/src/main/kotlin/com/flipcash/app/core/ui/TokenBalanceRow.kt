package com.flipcash.app.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.TokenWithBalance
import com.getcode.opencode.model.financial.TokenWithLocalizedBalance
import com.getcode.theme.CodeTheme

@Composable
fun TokenBalanceRow(
    tokenWithBalance: TokenWithLocalizedBalance,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val (token, balance) = tokenWithBalance
    TokenBalanceRow(
        token = token,
        balance = balance.nativeAmount,
        modifier = modifier,
        onClick = onClick
    )
}

@Composable
fun TokenBalanceRow(
    tokenWithBalance: TokenWithBalance,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val (token, balance) = tokenWithBalance
    TokenBalanceRow(
        token = token,
        balance = balance,
        modifier = modifier,
        onClick = onClick
    )
}


@Composable
fun TokenBalanceRow(
    token: Token,
    balance: Fiat,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .then(modifier)
            .padding(
                vertical = CodeTheme.dimens.inset,
            ),
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TokenIconWithName(
            token = token,
            imageSize = CodeTheme.dimens.staticGrid.x6,
            textStyle = CodeTheme.typography.screenTitle,
            textColor = CodeTheme.colors.textMain,
            spacing = CodeTheme.dimens.grid.x2,
        )

        Spacer(Modifier.weight(1f))

        Text(
            text = balance.formatted(),
            style = CodeTheme.typography.screenTitle,
            color = CodeTheme.colors.textMain,
        )
    }
}