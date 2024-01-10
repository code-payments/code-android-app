package com.getcode.view.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.getcode.theme.CodeTheme
import com.getcode.theme.White
import com.getcode.theme.White50

@Composable
fun CodeSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        interactionSource = interactionSource,
        colors = SwitchDefaults.colors(
            checkedThumbColor = White,
            uncheckedThumbColor = White50,
            checkedTrackColor = CodeTheme.colors.brandLight,
            checkedTrackAlpha = 1f,
            uncheckedTrackColor = CodeTheme.colors.brandLight
        )
    )
}