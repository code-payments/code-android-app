package com.getcode.ui.components

import android.graphics.Paint.Align
import android.view.ViewTreeObserver
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text2.BasicSecureTextField
import androidx.compose.foundation.text2.BasicTextField2
import androidx.compose.foundation.text2.input.TextFieldLineLimits
import androidx.compose.foundation.text2.input.TextFieldState
import androidx.compose.foundation.text2.input.TextObfuscationMode
import androidx.compose.foundation.text2.input.textAsFlow
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Text
import androidx.compose.material.TextFieldColors
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.getcode.theme.BrandLight
import com.getcode.theme.CodeTheme
import com.getcode.theme.extraSmall
import com.getcode.theme.inputColors
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterialApi::class)
@Composable
fun TextInput(
    modifier: Modifier = Modifier,
    placeholder: String = "",
    minLines: Int = 1,
    maxLines: Int = 4,
    state: TextFieldState,
    minHeight: Dp = 56.dp,
    contentPadding: PaddingValues = PaddingValues(),
    onStateChanged: () -> Unit = { },
    keyboardActions: KeyboardActions = KeyboardActions(),
    keyboardOptions: KeyboardOptions = KeyboardOptions(),
    style: TextStyle = CodeTheme.typography.textMedium,
    placeholderStyle: TextStyle = CodeTheme.typography.textMedium,
    shape: Shape = CodeTheme.shapes.extraSmall,
    colors: TextFieldColors = inputColors(),
    enabled: Boolean = true,
    readOnly: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    scrollState: ScrollState = rememberScrollState(),
) {
    val backgroundColor by colors.backgroundColor(enabled = enabled)
    val textColor by colors.textColor(enabled = enabled)
    val placeholderColor by colors.placeholderColor(enabled = enabled)
    val borderColor by colors.indicatorColor(
        enabled = enabled,
        isError = false,
        interactionSource = remember { MutableInteractionSource() }
    )

    BasicTextField2(
        modifier = modifier
            .background(backgroundColor, shape)
            .defaultMinSize(minHeight = minHeight),
        enabled = enabled,
        readOnly = readOnly,
        state = state,
        cursorBrush = SolidColor(colors.cursorColor(isError = false).value),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        textStyle = style.copy(color = textColor),
        lineLimits = if (maxLines == 1) {
            TextFieldLineLimits.SingleLine
        } else {
            TextFieldLineLimits.MultiLine(minHeightInLines = minLines, maxHeightInLines = maxLines)
        },
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
                shape = shape,
                innerTextField = it
            )
        },
        scrollState = scrollState
    )

    LaunchedEffect(Unit) {
        state.textAsFlow()
            .onEach { onStateChanged() }
            .launchIn(this)
    }

    val focusManager = LocalFocusManager.current
    val keyboardState by keyboardAsState()
    LaunchedEffect(keyboardState) {
        if (!keyboardState) {
            focusManager.clearFocus(true)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DecoratorBox(
    state: TextFieldState,
    placeholder: String,
    placeholderStyle: TextStyle,
    placeholderColor: Color,
    borderColor: Color = BrandLight,
    contentPadding: PaddingValues,
    leadingIcon: (@Composable () -> Unit)?,
    trailingIcon: (@Composable () -> Unit)?,
    shape: Shape,
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
                .weight(1f)
                .padding(horizontal = CodeTheme.dimens.staticGrid.x2),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(modifier = Modifier.padding(contentPadding)) {
                innerTextField()
            }
            if (state.text.isEmpty() && placeholder.isNotEmpty()) {
                Text(
                    modifier = Modifier.then(Modifier.padding(contentPadding)),
                    text = placeholder,
                    style = placeholderStyle.copy(color = placeholderColor),
                    maxLines = 1,
                )
            }
        }
        trailingIcon?.invoke()
    }
}

@Composable
fun keyboardAsState(): State<Boolean> {
    val keyboardState = remember { mutableStateOf(false) }
    val view = LocalView.current
    val viewTreeObserver = view.viewTreeObserver
    DisposableEffect(viewTreeObserver) {
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            keyboardState.value = ViewCompat.getRootWindowInsets(view)
                ?.isVisible(WindowInsetsCompat.Type.ime()) ?: true
        }
        viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose { viewTreeObserver.removeOnGlobalLayoutListener(listener) }
    }
    return keyboardState
}