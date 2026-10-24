package com.getcode.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import com.getcode.theme.CodeTheme

object PriceWithFlagDefaults {

    @Composable
    fun Text(label: String, style: TextStyle = LocalTextStyle.current) {
        Text(
            text = label,
            color = Color.Black,
            style = style,
        )
    }
}

@Composable
fun PriceWithFlag(
    modifier: Modifier = Modifier,
    currencyCode: String,
    amount: String,
    flag: Int?,
    iconSize: Dp = CodeTheme.dimens.staticGrid.x4,
    text: @Composable (String) -> Unit = { PriceWithFlagDefaults.Text(label = it) },
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2)
    ) {
        if (flag != null) {
            Icon(
                modifier = Modifier
                    .clip(CircleShape)
                    .size(iconSize),
                painter = painterResource(id = flag),
                tint = Color.Unspecified,
                contentDescription = currencyCode.let { "$it flag" }
            )
            text(amount)
        }
    }
}