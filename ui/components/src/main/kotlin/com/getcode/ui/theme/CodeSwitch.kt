package com.getcode.ui.theme

import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.getcode.theme.CodeTheme
import com.getcode.theme.SystemGreen
import com.getcode.theme.White

object CodeToggleSwitchDefaults {

    private const val DISABLED_ALPHA = 0.38f

    val colors: SwitchColors
        @Composable get() = SwitchDefaults.colors(
            disabledCheckedThumbColor = Color(0x80B2B9B5),
            disabledUncheckedThumbColor = Color(0x80B2B9B5),
            disabledCheckedTrackColor = Color(0xFF2A8A4B),
            disabledUncheckedTrackColor = CodeTheme.colors.toggleUncheckedTrackColor.copy(DISABLED_ALPHA),
            checkedThumbColor = White,
            uncheckedThumbColor = White,
            checkedTrackColor = SystemGreen,
            uncheckedTrackColor = CodeTheme.colors.toggleUncheckedTrackColor,
            uncheckedBorderColor = Color.White.copy(0.15f),
            checkedBorderColor = SystemGreen,
            disabledUncheckedBorderColor = CodeTheme.colors.toggleUncheckedTrackColor.copy(DISABLED_ALPHA),
            disabledCheckedBorderColor = Color(0xFF2A8A4B),
        )
}

@Composable
fun CodeToggleSwitch(
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onCheckedChange: ((Boolean) -> Unit)? = null,
) {
    Switch(
        checked = checked,
        modifier = modifier,
        enabled = enabled,
        onCheckedChange = onCheckedChange,
        colors = CodeToggleSwitchDefaults.colors
    )
}