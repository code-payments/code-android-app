package com.getcode.ui.theme

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.ContentAlpha
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxColors
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.getcode.theme.White
import com.getcode.theme.White50

object CodeCheckboxDefaults {

    val colors: CheckboxColors
        @Composable get() = CheckboxDefaults.colors(
            checkedColor = White,
            uncheckedColor = White50,
            checkmarkColor = Color.Black,
            disabledCheckedColor = White.copy(ContentAlpha.disabled),
            disabledUncheckedColor = White50.copy(ContentAlpha.disabled),
        )
}

@Composable
fun CodeCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) {
    Checkbox(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = CodeCheckboxDefaults.colors,
        interactionSource = interactionSource,
    )
}