package com.getcode.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Divider
import androidx.compose.material.LocalContentColor
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.CodeToggleSwitch
import com.getcode.ui.utils.rememberedClickable

@Composable
fun SettingsSwitchRow(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    title: String,
    icon: Int? = null,
    subtitle: String? = null,
    checked: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .rememberedClickable(enabled) { onClick() }
            .padding(horizontal = CodeTheme.dimens.grid.x3),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val contentColor by animateColorAsState(
            if (enabled) LocalContentColor.current
            else LocalContentColor.current.copy(ContentAlpha.disabled),
            label = "content color"
        )

        if (icon != null) {
            Image(
                modifier = Modifier
                    .padding(end = CodeTheme.dimens.inset)
                    .height(CodeTheme.dimens.staticGrid.x5)
                    .width(CodeTheme.dimens.staticGrid.x5),
                painter = painterResource(id = icon),
                colorFilter = ColorFilter.tint(contentColor),
                contentDescription = ""
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = CodeTheme.dimens.grid.x3)
        ) {
            Text(
                modifier = Modifier
                    .padding(vertical = CodeTheme.dimens.grid.x2),
                text = title,
                color = contentColor,
            )
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    modifier = Modifier
                        .padding(vertical = CodeTheme.dimens.grid.x1),
                    text = subtitle,
                    style = CodeTheme.typography.caption,
                    color = CodeTheme.colors.textSecondary
                        .copy(alpha = if (enabled) 1f else ContentAlpha.disabled)
                )
            }
        }

        CodeToggleSwitch(
            enabled = enabled,
            checked = checked,
            onCheckedChange = null,
        )
    }
}

@Composable
fun SettingsRow(
    modifier: Modifier = Modifier,
    title: String,
    icon: Painter?,
    onClick: () -> Unit
) {
    Column(modifier = Modifier.rememberedClickable { onClick() }.then(modifier)) {
        Row(
            modifier = Modifier
                .padding(horizontal = CodeTheme.dimens.inset)
                .padding(vertical = CodeTheme.dimens.grid.x5)
                .fillMaxWidth()
                .wrapContentHeight(),
            verticalAlignment = CenterVertically
        ) {
            val imageMod = Modifier
                .padding(end = CodeTheme.dimens.inset)
                .height(CodeTheme.dimens.staticGrid.x5)
                .width(CodeTheme.dimens.staticGrid.x5)

            if (icon != null) {
                Image(
                    modifier = imageMod,
                    painter = icon,
                    colorFilter = ColorFilter.tint(Color.White),
                    contentDescription = ""
                )
            } else {
                Spacer(modifier = imageMod)
            }
            Text(
                modifier = Modifier.align(CenterVertically),
                text = title,
                style = CodeTheme.typography.textLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
            )
        }

        Divider(
            color = CodeTheme.colors.divider,
            thickness = 0.5.dp
        )
    }
}