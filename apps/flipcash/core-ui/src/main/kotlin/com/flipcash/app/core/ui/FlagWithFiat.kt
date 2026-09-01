package com.flipcash.app.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import com.getcode.opencode.compose.LocalExchange
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.theme.CodeTheme

@Composable
fun FlagWithFiat(
    fiat: Fiat,
    modifier: Modifier = Modifier,
    extraPrefix: String? = null,
    suffix: @Composable (CurrencyCode) -> String? = { null },
    iconSize: Dp = CodeTheme.dimens.staticGrid.x4,
    spacing: Dp = CodeTheme.dimens.grid.x2,
    textStyle: TextStyle = CodeTheme.typography.textMedium,
    textColor: Color = CodeTheme.colors.textMain,
) {
    val exchange = LocalExchange.current
    val flag = exchange.getFlagByCurrency(fiat.currencyCode.name)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        flag?.let {
            Image(
                modifier = Modifier
                    .padding(end = spacing)
                    .height(iconSize)
                    .width(iconSize)
                    .clip(CircleShape),
                painter = painterResource(it),
                contentDescription = ""
            )
        }

        Text(
            text = fiat.formatted(extraPrefix = extraPrefix),
            style = textStyle,
            color = textColor,
        )
    }
}