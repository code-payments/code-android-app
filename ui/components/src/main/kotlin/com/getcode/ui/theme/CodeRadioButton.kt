package com.getcode.ui.theme

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.ContentAlpha
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonColors
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.getcode.theme.White
import com.getcode.theme.White50

object CodeRadioButtonDefaults {
    val colors: RadioButtonColors
        @Composable get() = RadioButtonDefaults.colors(
            selectedColor = White,
            unselectedColor = White50,
            disabledSelectedColor = White.copy(ContentAlpha.disabled),
            disabledUnselectedColor = White50.copy(ContentAlpha.disabled),
        )
}

@Composable
fun CodeRadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) {
    RadioButton(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = CodeRadioButtonDefaults.colors,
        interactionSource = interactionSource,
    )
}