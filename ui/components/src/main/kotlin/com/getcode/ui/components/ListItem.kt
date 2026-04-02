package com.getcode.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.rememberedClickable

@Composable
fun ListItem(
    headline: String,
    icon: Painter?,
    modifier: Modifier = Modifier,
    showBetaIndicator: Boolean = false,
    showChevron: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .rememberedClickable { onClick() }
            .padding(CodeTheme.dimens.grid.x5)
            .fillMaxWidth()
            .wrapContentHeight(),
        verticalAlignment = CenterVertically
    ) {
        if (icon != null) {
            Image(
                modifier = Modifier
                    .padding(end = CodeTheme.dimens.inset)
                    .height(CodeTheme.dimens.staticGrid.x5)
                    .width(CodeTheme.dimens.staticGrid.x5),
                painter = icon,
                colorFilter = ColorFilter.tint(CodeTheme.colors.onBackground),
                contentDescription = ""
            )
        }

        Text(
            modifier = Modifier.align(CenterVertically),
            text = headline,
            style = CodeTheme.typography.textLarge.copy(
                fontWeight = FontWeight.Bold
            ),
            color = CodeTheme.colors.textMain,
        )

        Spacer(Modifier.weight(1f))

        if (showBetaIndicator) {
            BetaIndicator()
        }

        if (showChevron) {
            Icon(
                painter = painterResource(id = R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = CodeTheme.colors.secondary,
            )
        }
    }

    HorizontalDivider(
        modifier = Modifier.padding(horizontal = CodeTheme.dimens.inset),
        color = CodeTheme.colors.divider,
        thickness = 0.5.dp
    )
}