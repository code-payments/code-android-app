package com.getcode.ui.components

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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.Text
import androidx.compose.material.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.layout
import androidx.compose.ui.node.Ref
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.getcode.theme.CodeTheme
import com.getcode.theme.extraSmall
import com.getcode.theme.inputColors
import com.getcode.ui.utils.AutoSizeTextMeasurer
import com.getcode.ui.utils.addIf
import com.getcode.ui.utils.measured
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.math.roundToInt

sealed interface ConstraintMode {
    data object Free : ConstraintMode
    data class AutoSize(val minimum: TextStyle) : ConstraintMode
}

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
    keyboardOptions: KeyboardOptions = KeyboardOptions(),
    style: TextStyle = CodeTheme.typography.textMedium,
    placeholderStyle: TextStyle = CodeTheme.typography.textMedium,
    shape: Shape = CodeTheme.shapes.extraSmall,
    colors: TextFieldColors = inputColors(),
    enabled: Boolean = true,
    isError: Boolean = false,
    readOnly: Boolean = false,
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
                        maxWidth = with (density) { textFieldSize.width.roundToPx() },
                        maxHeight = with (density) { textFieldSize.height.roundToPx() },
                    )
                ) { textSize = it },
            enabled = enabled,
            readOnly = readOnly,
            state = state,
            cursorBrush = SolidColor(colors.cursorColor(isError = false).value),
            keyboardOptions = keyboardOptions,
            textStyle = style.copy(color = textColor, fontSize = textSize),
            lineLimits = if (maxLines == 1) {
                TextFieldLineLimits.SingleLine
            } else {
                TextFieldLineLimits.MultiLine(
                    minHeightInLines = minLines,
                    maxHeightInLines = maxLines
                )
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
    }

    LaunchedEffect(Unit) {
        snapshotFlow { state.text }
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
private fun Modifier.constrain(
    mode: ConstraintMode,
    state: TextFieldState,
    style: TextStyle,
    frameConstraints: Constraints,
    onTextSizeDetermined: (TextUnit) -> Unit
): Modifier = this.composed {
    val textMeasurer = rememberTextMeasurer()
    val autosizeTextMeasurer = remember(textMeasurer) { AutoSizeTextMeasurer(textMeasurer) }
    val textLayoutResult = remember { Ref<TextLayoutResult?>() }
    var flag by remember { mutableStateOf(Unit, neverEqualPolicy()) }

    Modifier.addIf(mode is ConstraintMode.AutoSize) {
        Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val result = autosizeTextMeasurer.measure(
                text = AnnotatedString(state.text.toString()),
                style = style,
                constraints = Constraints(
                    maxWidth = (frameConstraints.maxWidth * 0.85f).roundToInt(),
                    minHeight = 0
                ),
                minFontSize = (mode as ConstraintMode.AutoSize).minimum.fontSize,
                maxFontSize = style.fontSize,
                autosizeGranularity = 100
            )

            textLayoutResult.value = result
            flag = Unit

            onTextSizeDetermined(result.layoutInput.style.fontSize)

            layout(placeable.width, placeable.height) {
                placeable.placeRelative(0, 0)
            }
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
    borderColor: Color = CodeTheme.colors.brandLight,
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
                .addIf(leadingIcon != null) {
                    Modifier.padding(start = CodeTheme.dimens.staticGrid.x2)
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Box(modifier = Modifier.padding(contentPadding)) {
                innerTextField()
            }
            if (state.text.isEmpty() && placeholder.isNotEmpty()) {
                Text(
                    modifier = Modifier.fillMaxWidth().then(Modifier.padding(contentPadding)),
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