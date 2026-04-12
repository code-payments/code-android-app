package com.flipcash.app.core.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.getcode.theme.CodeTheme
import com.getcode.theme.White10
import com.getcode.theme.inputColors
import com.getcode.ui.components.TextInput
import kotlin.math.min

/**
 * A chromeless [TextInput] styled for prominent, display-sized text entry.
 * No background, no border — just large text and a subtle placeholder.
 *
 * Used for hero inputs like currency name entry.
 */
@Composable
fun DisplayTextInput(
    state: TextFieldState,
    placeholder: String,
    modifier: Modifier = Modifier,
    style: TextStyle = CodeTheme.typography.displayMedium.copy(
        color = CodeTheme.colors.textMain,
        fontWeight = FontWeight.SemiBold,
    ),
    placeholderStyle: TextStyle = style.copy(color = CodeTheme.colors.textTertiary),
    minLines: Int = 1,
    maxLines: Int = 1,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
    inputTransformation: InputTransformation? = null,
) {
    TextInput(
        state = state,
        modifier = modifier,
        placeholder = placeholder,
        style = style,
        placeholderStyle = placeholderStyle,
        textFieldAlignment = Alignment.TopStart,
        maxLines = maxLines,
        minLines = minLines,
        minHeight = 0.dp,
        shape = RectangleShape,
        enabled = enabled,
        colors = inputColors(
            backgroundColor = Color.Transparent,
            borderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            placeholderColor = placeholderStyle.color,
        ),
        keyboardOptions = keyboardOptions,
        onKeyboardAction = onKeyboardAction,
        inputTransformation = inputTransformation,
    )
}
