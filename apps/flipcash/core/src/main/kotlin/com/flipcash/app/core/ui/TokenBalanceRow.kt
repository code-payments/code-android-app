package com.flipcash.app.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.flipcash.core.R
import com.getcode.opencode.compose.LocalExchange
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.TokenWithBalance
import com.getcode.opencode.model.financial.TokenWithLocalizedBalance
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.addIf

@Composable
fun TokenBalanceRow(
    tokenWithBalance: TokenWithLocalizedBalance,
    modifier: Modifier = Modifier,
    showName: Boolean = true,
    showFlag: Boolean = false,
    isSelected: Boolean? = null,
    formattedBalance: (Fiat) -> String = { it.formatted() },
    horizontalArrangement: Arrangement.Horizontal = Arrangement.SpaceBetween,
    nameTextStyle: TextStyle = CodeTheme.typography.screenTitle,
    balanceTextStyle: TextStyle = CodeTheme.typography.screenTitle,
    contentPadding: PaddingValues = PaddingValues(vertical = CodeTheme.dimens.inset),
    onClick: (() -> Unit)? = null,
) {
    val (token, balance) = tokenWithBalance
    TokenBalanceRow(
        token = token,
        balance = balance.nativeAmount,
        formattedBalance = formattedBalance,
        nameTextStyle = nameTextStyle,
        balanceTextStyle = balanceTextStyle,
        isSelected = isSelected,
        modifier = modifier,
        showName = showName,
        showFlag = showFlag,
        horizontalArrangement = horizontalArrangement,
        contentPadding = contentPadding,
        onClick = onClick
    )
}

@Composable
fun TokenBalanceRow(
    tokenWithBalance: TokenWithBalance,
    modifier: Modifier = Modifier,
    showName: Boolean = true,
    showFlag: Boolean = false,
    isSelected: Boolean? = null,
    formattedBalance: (Fiat) -> String = { it.formatted() },
    horizontalArrangement: Arrangement.Horizontal = Arrangement.SpaceBetween,
    nameTextStyle: TextStyle = CodeTheme.typography.screenTitle,
    balanceTextStyle: TextStyle = CodeTheme.typography.screenTitle,
    contentPadding: PaddingValues = PaddingValues(vertical = CodeTheme.dimens.inset),
    onClick: (() -> Unit)? = null,
) {
    val (token, balance) = tokenWithBalance
    TokenBalanceRow(
        token = token,
        balance = balance,
        showName = showName,
        showFlag = showFlag,
        isSelected = isSelected,
        modifier = modifier,
        nameTextStyle = nameTextStyle,
        balanceTextStyle = balanceTextStyle,
        formattedBalance = formattedBalance,
        horizontalArrangement = horizontalArrangement,
        contentPadding = contentPadding,
        onClick = onClick
    )
}


@Composable
fun TokenBalanceRow(
    token: Token,
    balance: Fiat,
    modifier: Modifier = Modifier,
    showName: Boolean = true,
    showFlag: Boolean = false,
    isSelected: Boolean? = null,
    formattedBalance: (Fiat) -> String = { it.formatted() },
    horizontalArrangement: Arrangement.Horizontal = Arrangement.SpaceBetween,
    nameTextStyle: TextStyle = CodeTheme.typography.screenTitle,
    balanceTextStyle: TextStyle = CodeTheme.typography.screenTitle,
    contentPadding: PaddingValues = PaddingValues(vertical = CodeTheme.dimens.inset),
    onClick: (() -> Unit)? = null,
) {
    val exchange = LocalExchange.current
    Row(
        modifier = Modifier
            .addIf(onClick != null) {
                Modifier.clickable {
                    onClick?.invoke()
                }
            }
            .then(modifier)
            .padding(contentPadding),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showName) {
            TokenIconWithName(
                token = token,
                imageSize = CodeTheme.dimens.staticGrid.x6,
                textStyle = nameTextStyle,
                textColor = CodeTheme.colors.textMain,
                spacing = CodeTheme.dimens.grid.x2,
            )
        } else {
            TokenIcon(
                modifier = Modifier.size(CodeTheme.dimens.staticGrid.x6,),
                token = token,
            )
        }

        val flag = exchange.getFlagByCurrency(balance.currencyCode.name)
        Row(
            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showFlag) {
                flag?.let {
                    Image(
                        modifier = Modifier
                            .height(CodeTheme.dimens.staticGrid.x3)
                            .width(CodeTheme.dimens.staticGrid.x3)
                            .clip(CircleShape),
                        painter = painterResource(it),
                        contentDescription = ""
                    )
                }
            }

            Text(
                text = formattedBalance(balance),
                style = balanceTextStyle,
                color = CodeTheme.colors.textMain,
            )

            if (isSelected != null) {
                Image(
                    modifier = Modifier
                        .wrapContentWidth()
                        .padding(start = CodeTheme.dimens.grid.x3),
                    painter = painterResource(
                        if (isSelected)
                            R.drawable.ic_checked else R.drawable.ic_unchecked
                    ),
                    contentDescription = ""
                )
            }
        }
    }
}