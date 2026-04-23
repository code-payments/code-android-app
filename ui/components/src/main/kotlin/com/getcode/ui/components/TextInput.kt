package com.getcode.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.Text
import androidx.compose.material.TextFieldColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.datasource.LoremIpsum
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.getcode.theme.CodeTheme
import com.getcode.theme.DesignSystem
import com.getcode.theme.extraSmall
import com.getcode.theme.inputColors
import com.getcode.ui.core.addIf
import com.getcode.ui.core.measured
import com.getcode.ui.utils.ConstraintMode
import com.getcode.ui.utils.constrain
import com.getcode.ui.utils.rememberKeyboardController
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Composable
fun TextInput(
    modifier: Modifier = Modifier,
    textModifier: Modifier = Modifier,
    placeholder: String = "",
    minLines: Int = 1,
    maxLines: Int = 4,
    state: TextFieldState,
    minHeight: Dp = 56.dp,
    contentPadding: PaddingValues = PaddingValues(),
    onStateChanged: () -> Unit = { },
    keyboardOptions: KeyboardOptions = KeyboardOptions(),
    onKeyboardAction: KeyboardActionHandler? = null,
    style: TextStyle = CodeTheme.typography.textMedium,
    placeholderStyle: TextStyle = CodeTheme.typography.textMedium,
    shape: Shape = CodeTheme.shapes.extraSmall,
    textFieldAlignment: Alignment = Alignment.CenterStart,
    colors: TextFieldColors = inputColors(),
    enabled: Boolean = true,
    isError: Boolean = false,
    readOnly: Boolean = false,
    inputTransformation: InputTransformation? = null,
    outputTransformation: OutputTransformation? = null,
    constraintMode: ConstraintMode = ConstraintMode.Free,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    scrollState: ScrollState = rememberScrollState(),
) {
    val backgroundColor by colors.backgroundColor(enabled = enabled)
    val textColor by colors.textColor(enabled = enabled)
    val placeholderColor by colors.placeholderColor(enabled = enabled)
    val borderColor by colors.indicatorColor(
        enabled = enabled,
        isError = isError,
        interactionSource = remember { MutableInteractionSource() }
    )

    val density = LocalDensity.current
    var textSize by remember { mutableStateOf(style.fontSize) }
    var textFieldSize by remember { mutableStateOf(DpSize.Zero) }

    Box(modifier = modifier.measured { textFieldSize = it }) {
        BasicTextField(
            modifier = Modifier
                .background(backgroundColor, shape)
                .defaultMinSize(minHeight = minHeight)
                .constrain(
                    mode = constraintMode,
                    state = state,
                    style = style,
                    frameConstraints = Constraints(
                        minWidth = 0,
                        minHeight = 0,
                        maxWidth = with(density) { textFieldSize.width.roundToPx() },
                        maxHeight = with(density) { textFieldSize.height.roundToPx() },
                    )
                ) { textSize = it },
            enabled = enabled,
            readOnly = readOnly,
            state = state,
            cursorBrush = SolidColor(colors.cursorColor(isError = false).value),
            keyboardOptions = keyboardOptions,
            onKeyboardAction = onKeyboardAction,
            textStyle = style.copy(color = textColor, fontSize = textSize),
            lineLimits = when (maxLines) {
                1 -> TextFieldLineLimits.SingleLine
                Int.MAX_VALUE -> TextFieldLineLimits.MultiLine(
                    minHeightInLines = minLines,
                )
                else -> TextFieldLineLimits.MultiLine(
                    minHeightInLines = minLines,
                    maxHeightInLines = maxLines,
                )
            },
            inputTransformation = inputTransformation,
            outputTransformation = outputTransformation,
            decorator = {
                DecoratorBox(
                    state = state,
                    placeholder = placeholder,
                    placeholderStyle = placeholderStyle,
                    placeholderColor = placeholderColor,
                    borderColor = borderColor,
                    leadingIcon = leadingIcon,
                    trailingIcon = trailingIcon,
                    contentPadding = contentPadding,
                    textFieldAlignment = textFieldAlignment,
                    shape = shape,
                    innerTextField = it,
                    modifier = textModifier,
                )
            },
            scrollState = scrollState
        )
    }

    LaunchedEffect(Unit) {
        snapshotFlow { state.text }
            .onEach { onStateChanged() }
            .launchIn(this)
    }

    val focusManager = LocalFocusManager.current
    val keyboardController = rememberKeyboardController()
    LaunchedEffect(keyboardController.visible) {
        if (!keyboardController.visible) {
            focusManager.clearFocus(true)
        }
    }
}

@Composable
private fun DecoratorBox(
    state: TextFieldState,
    placeholder: String,
    placeholderStyle: TextStyle,
    placeholderColor: Color,
    modifier: Modifier = Modifier,
    borderColor: Color = CodeTheme.colors.brandLight,
    contentPadding: PaddingValues,
    leadingIcon: (@Composable () -> Unit)?,
    trailingIcon: (@Composable () -> Unit)?,
    shape: Shape,
    textFieldAlignment: Alignment = Alignment.CenterStart,
    innerTextField: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = CodeTheme.dimens.border,
                color = borderColor,
                shape = shape,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.staticGrid.x2)
    ) {
        leadingIcon?.invoke()
        Box(
            modifier = Modifier
                .weight(1f),
            contentAlignment = textFieldAlignment
        ) {
            Box(modifier = Modifier.padding(contentPadding).then(modifier)) {
                innerTextField()
            }
            if (state.text.isEmpty() && placeholder.isNotEmpty()) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .addIf(textFieldAlignment == Alignment.Center) {
                            Modifier.align(Alignment.Center)
                        }
                        .then(Modifier.padding(contentPadding)),
                    text = placeholder,
                    style = placeholderStyle.copy(color = placeholderColor),
                    maxLines = 1,
                )
            }
        }
        trailingIcon?.invoke()
    }
}

// region Previews

@Preview
@Composable
private fun TextInputEmptyPreview() {
    DesignSystem {
        TextInput(
            state = remember { TextFieldState() },
            placeholder = "Enter text",
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
    }
}

@Preview
@Composable
private fun TextInputWithTextPreview() {
    DesignSystem {
        TextInput(
            state = remember { TextFieldState("Hello, world!") },
            placeholder = "Enter text",
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
    }
}

@Preview
@Composable
private fun TextInputSingleLinePreview() {
    DesignSystem {
        TextInput(
            state = remember { TextFieldState() },
            placeholder = "Single line",
            maxLines = 1,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
    }
}

@Preview
@Composable
private fun TextInputMultiLinePreview() {
    DesignSystem {
        TextInput(
            state = remember { TextFieldState("Line one\nLine two\nLine three") },
            placeholder = "Multi line",
            maxLines = 4,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
    }
}

@Preview
@Composable
private fun TextInputUnboundedEmptyPreview() {
    DesignSystem {
        TextInput(
            state = remember { TextFieldState() },
            placeholder = "Unbounded lines",
            maxLines = Int.MAX_VALUE,
            minHeight = 0.dp,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
    }
}

@Preview
@Composable
private fun TextInputUnboundedWithTextPreview() {
    DesignSystem {
        TextInput(
            state = remember { TextFieldState("First line\nSecond line\nThird line") },
            placeholder = "Unbounded lines",
            maxLines = Int.MAX_VALUE,
            minHeight = 0.dp,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
    }
}

@Preview
@Composable
private fun TextInputDisabledPreview() {
    DesignSystem {
        TextInput(
            state = remember { TextFieldState("Disabled input") },
            placeholder = "Disabled",
            enabled = false,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
    }
}

@Preview
@Composable
private fun TextInputErrorPreview() {
    DesignSystem {
        TextInput(
            state = remember { TextFieldState("Invalid value") },
            placeholder = "Error state",
            isError = true,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
    }
}

@Preview
@Composable
private fun TextInputUnboundedLoremIpsumPreview() {
    DesignSystem {
        TextInput(
            state = remember {
                TextFieldState(LoremIpsum(1000).values.joinToString(" "))
            },
            placeholder = "Unbounded lines",
            maxLines = Int.MAX_VALUE,
            minHeight = 0.dp,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
    }
}

@Preview
@Composable
private fun TextInputWithIconsPreview() {
    DesignSystem {
        TextInput(
            state = remember { TextFieldState() },
            placeholder = "Search...",
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 12.dp),
                    tint = Color.White,
                )
            },
        )
    }
}

// endregion