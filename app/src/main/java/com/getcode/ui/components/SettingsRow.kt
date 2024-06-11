package com.getcode.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.getcode.theme.CodeTheme
import com.getcode.ui.utils.rememberedClickable

@Composable
fun SettingsRow(
    modifier: Modifier = Modifier,
    title: String,
    icon: Int? = null,
    subtitle: String? = null,
    checked: Boolean,
    onClick: () -> Unit) {
    Row(
        modifier = modifier
            .rememberedClickable { onClick() }
            .padding(horizontal = CodeTheme.dimens.grid.x3)
            .padding(end = CodeTheme.dimens.grid.x3),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Image(
                modifier = Modifier
                    .padding(end = CodeTheme.dimens.inset)
                    .height(CodeTheme.dimens.staticGrid.x5)
                    .width(CodeTheme.dimens.staticGrid.x5),
                painter = painterResource(id = icon),
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
            )
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    modifier = Modifier
                        .padding(vertical = CodeTheme.dimens.grid.x1),
                    text = subtitle,
                    style = CodeTheme.typography.caption,
                    color = CodeTheme.colors.textSecondary
                )
            }
        }

        CodeToggleSwitch(
            checked = checked,
            onCheckedChange = null,
        )
    }
}