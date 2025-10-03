package com.flipcash.app.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.error
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.TokenWithBalance
import com.getcode.opencode.model.financial.TokenWithLocalizedBalance
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.R

@Composable
fun TokenBalanceRow(
    tokenWithBalance: TokenWithLocalizedBalance,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val (token, balance) = tokenWithBalance
    TokenBalanceRow(
        token = token,
        balance = balance.converted,
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
        AsyncImage(
            modifier = Modifier
                .size(CodeTheme.dimens.staticGrid.x5)
                .clip(CircleShape),
            model = ImageRequest.Builder(LocalPlatformContext.current)
                .data(token.imageUrl)
                .error(R.drawable.ic_placeholder_user)
                .placeholderMemoryCacheKey(token.symbol)
                .build(),
            contentDescription = null,
        )

        Text(
            text = token.name,
            style = CodeTheme.typography.screenTitle,
            color = CodeTheme.colors.textMain,
        )

        Spacer(Modifier.weight(1f))

        Text(
            text = balance.formatted(),
            style = CodeTheme.typography.screenTitle,
            color = CodeTheme.colors.textMain,
        )
    }
}