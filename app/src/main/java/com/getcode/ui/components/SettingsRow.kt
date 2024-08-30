package com.getcode.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.ContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import com.getcode.theme.CodeTheme
import com.getcode.ui.utils.rememberedClickable

@Composable
fun SettingsRow(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    title: String,
    icon: Int? = null,
    subtitle: String? = null,
    checked: Boolean,
    onClick: () -> Unit) {
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