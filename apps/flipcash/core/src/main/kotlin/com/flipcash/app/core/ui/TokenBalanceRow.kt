package com.flipcash.app.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flipcash.core.R
import com.getcode.opencode.compose.LocalExchange
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.TokenWithBalance
import com.getcode.opencode.model.financial.TokenWithLocalizedBalance
import com.getcode.theme.CodeTheme
import com.getcode.theme.White20
import com.getcode.theme.extraSmall
import com.getcode.ui.components.text.AnimatedNumberText
import com.getcode.ui.core.addIf

sealed interface TokenBalanceStyle {
    val textStyle: TextStyle

    data class Large(override val textStyle: TextStyle = TextStyle.Default) : TokenBalanceStyle
    data class Pill(override val textStyle: TextStyle = TextStyle.Default) : TokenBalanceStyle
}

enum class TokenSelectionStyle {
    None,
    Chevron,
    Checkbox,
    ;
}

data class TokenBalanceRowStyling(
    val nameTextStyle: TextStyle,
    val balanceDisplayStyle: TokenBalanceStyle,
    val iconSize: Dp,
    val flagSize: Dp,
    val selectionStyle: TokenSelectionStyle,
)

@Composable
fun rememberTokenBalanceRowStyling(
    nameTextStyle: TextStyle = CodeTheme.typography.screenTitle,
    balanceDisplayStyle: TokenBalanceStyle = TokenBalanceStyle.Large(),
    iconSize: Dp = CodeTheme.dimens.staticGrid.x6,
    flagSize: Dp = CodeTheme.dimens.staticGrid.x3,
    selectionStyle: TokenSelectionStyle = TokenSelectionStyle.None,
): TokenBalanceRowStyling =
    TokenBalanceRowStyling(
        nameTextStyle = nameTextStyle,
        balanceDisplayStyle = balanceDisplayStyle,
        iconSize = iconSize,
        flagSize = flagSize,
        selectionStyle = selectionStyle
    )

@Composable
fun TokenBalanceRow(
    tokenWithBalance: TokenWithLocalizedBalance,
    modifier: Modifier = Modifier,
    showName: Boolean = true,
    showFlag: Boolean = false,
    showLogo: Boolean = true,
    isSelected: Boolean? = null,
    iconOverride: @Composable ((Any?) -> Any?) = { it },
    formattedBalance: (Fiat) -> String = { it.formatted() },
    horizontalArrangement: Arrangement.Horizontal = Arrangement.SpaceBetween,
    styling: TokenBalanceRowStyling = rememberTokenBalanceRowStyling(),
    contentPadding: PaddingValues = PaddingValues(vertical = CodeTheme.dimens.inset),
    onClick: (() -> Unit)? = null,
) {
    val (token, balance, _, displayName) = tokenWithBalance
    TokenBalanceRow(
        token = token,
        displayName = displayName,
        balance = balance.nativeAmount,
        iconOverride = iconOverride,
        formattedBalance = formattedBalance,
        isSelected = isSelected,
        modifier = modifier,
        showName = showName,
        showFlag = showFlag,
        showLogo = showLogo,
        styling = styling,
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
    showLogo: Boolean = true,
    showFlag: Boolean = false,
    isSelected: Boolean? = null,
    iconOverride: @Composable ((Any?) -> Any?) = { it },
    formattedBalance: (Fiat) -> String = { it.formatted() },
    horizontalArrangement: Arrangement.Horizontal = Arrangement.SpaceBetween,
    styling: TokenBalanceRowStyling = rememberTokenBalanceRowStyling(),
    contentPadding: PaddingValues = PaddingValues(vertical = CodeTheme.dimens.inset),
    onClick: (() -> Unit)? = null,
) {
    val (token, balance, _, displayName) = tokenWithBalance
    TokenBalanceRow(
        token = token,
        displayName = displayName,
        balance = balance,
        showName = showName,
        showLogo = showLogo,
        showFlag = showFlag,
        isSelected = isSelected,
        modifier = modifier,
        styling = styling,
        iconOverride = iconOverride,
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
    displayName: String = token.name,
    showName: Boolean = true,
    showLogo: Boolean = true,
    showFlag: Boolean = false,
    isSelected: Boolean? = null,
    iconOverride: @Composable ((Any?) -> Any?) = { it },
    formattedBalance: (Fiat) -> String = { it.formatted() },
    horizontalArrangement: Arrangement.Horizontal = Arrangement.SpaceBetween,
    styling: TokenBalanceRowStyling = rememberTokenBalanceRowStyling(),
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
        when {
            showLogo && showName -> {
                TokenIconWithName(
                    token = token,
                    iconOverride = iconOverride,
                    displayName = { displayName },
                    imageSize = styling.iconSize,
                    textStyle = styling.nameTextStyle,
                    textColor = CodeTheme.colors.textMain,
                    spacing = CodeTheme.dimens.grid.x2,
                )
            }

            showLogo && !showName -> {
                when (val image = iconOverride(token.imageUrl)) {
                    is Painter -> Image(
                        painter = image,
                        contentDescription = null,
                        modifier = Modifier.size(styling.iconSize),
                    )

                    else -> TokenIcon(
                        image = image,
                        modifier = Modifier.size(styling.iconSize)
                    )
                }
            }

            showName -> {
                Text(
                    text = displayName,
                    style = styling.nameTextStyle,
                    color = CodeTheme.colors.textMain,
                )
            }

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
                            .height(styling.flagSize)
                            .width(styling.flagSize)
                            .clip(CircleShape),
                        painter = painterResource(it),
                        contentDescription = ""
                    )
                }
            }

            when (val displayStyle = styling.balanceDisplayStyle) {
                is TokenBalanceStyle.Large -> {
                    val resolvedTextStyle = displayStyle.textStyle
                        .takeUnless { it == TextStyle.Default }
                        ?: CodeTheme.typography.screenTitle

                    AnimatedNumberText(
                        value = formattedBalance(balance),
                        style = resolvedTextStyle,
                        color = CodeTheme.colors.textMain,
                    )
                }

                is TokenBalanceStyle.Pill -> {
                    val resolvedTextStyle = displayStyle.textStyle
                        .takeUnless { it == TextStyle.Default }
                        ?: CodeTheme.typography.caption

                    Text(
                        modifier = Modifier
                            .padding(start = CodeTheme.dimens.grid.x1)
                            .border(
                                width = CodeTheme.dimens.border,
                                color = White20,
                                shape = CodeTheme.shapes.extraSmall,
                            )
                            .padding(
                                horizontal = 4.dp,
                                vertical = 3.dp
                            ),
                        text = formattedBalance(balance),
                        color = CodeTheme.colors.textSecondary,
                        style = resolvedTextStyle,
                    )
                }
            }

            when (styling.selectionStyle) {
                TokenSelectionStyle.Chevron -> {
                    Icon(
                        modifier = Modifier.padding(start = CodeTheme.dimens.grid.x1),
                        painter = painterResource(R.drawable.ic_chevron_right),
                        contentDescription = null,
                        tint = CodeTheme.colors.secondary,
                    )
                }

                TokenSelectionStyle.Checkbox -> {
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

                TokenSelectionStyle.None -> Unit
            }
        }
    }
}
