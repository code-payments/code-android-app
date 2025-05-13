package com.getcode.ui.theme

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.ButtonColors
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.ButtonDefaults.elevation
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.LocalMinimumInteractiveComponentEnforcement
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.Text
import androidx.compose.material.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.isUnspecified
import com.getcode.theme.CodeTheme
import com.getcode.theme.Transparent
import com.getcode.theme.White
import com.getcode.theme.White10
import com.getcode.theme.White20
import com.getcode.theme.White50
import com.getcode.ui.components.R
import com.getcode.ui.core.addIf
import com.getcode.ui.core.measured
import com.getcode.ui.utils.plus

enum class ButtonState {
    Bordered,
    Filled,
    Filled50,
    Subtle
}

@Composable
fun CodeButton(
    text: String,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    isSuccess: Boolean = false,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(
        top = CodeTheme.dimens.grid.x2,
        bottom = CodeTheme.dimens.grid.x2,
    ),
    overrideContentPadding: Boolean = false,
    buttonState: ButtonState = ButtonState.Bordered,
    textColor: Color = Color.Unspecified,
    shape: Shape = CodeTheme.shapes.small,
    style: TextStyle = CodeTheme.typography.textMedium,
    onClick: () -> Unit,
) {
    CodeButton(
        modifier = modifier,
        onClick = onClick,
        isLoading = isLoading,
        isSuccess = isSuccess,
        enabled = enabled,
        buttonState = buttonState,
        contentPadding = contentPadding,
        overrideContentPadding = overrideContentPadding,
        shape = shape,
        contentColor = textColor,
        style = style,
        sizeKey = text
    ) {
        Text(text = text)
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun CodeButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    isLoading: Boolean = false,
    isSuccess: Boolean = false,
    enabled: Boolean = true,
    buttonState: ButtonState = ButtonState.Bordered,
    shape: Shape = CodeTheme.shapes.small,
    contentPadding: PaddingValues = PaddingValues(
        top = CodeTheme.dimens.grid.x2,
        bottom = CodeTheme.dimens.grid.x2,
    ),
    overrideContentPadding: Boolean = false,
    contentColor: Color = Color.Unspecified,
    style: TextStyle = CodeTheme.typography.textMedium,
    sizeKey: Any? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val isEnabled by remember(enabled, isLoading, isSuccess) {
        derivedStateOf { enabled && !isLoading && !isSuccess }
    }
    val isSuccessful by remember(isSuccess) {
        derivedStateOf { isSuccess }
    }

    val colors = getButtonColors(isEnabled, buttonState, contentColor)
    val border = getButtonBorder(buttonState, isEnabled)
    val ripple = getRipple(buttonState = buttonState)

    CompositionLocalProvider(
        LocalMinimumInteractiveComponentEnforcement provides false,
        LocalIndication provides ripple
    ) {

        var size by remember(sizeKey) {
            mutableStateOf(DpSize.Unspecified)
        }

        val cp =
            (if (overrideContentPadding) PaddingValues(0.dp) else ButtonDefaults.ContentPadding).plus(
                contentPadding
            )

        val contentState by remember(isLoading, isSuccessful) {
            derivedStateOf {
                when {
                    isLoading -> ButtonContentState.Loading
                    isSuccessful -> ButtonContentState.Successful
                    else -> ButtonContentState.Content
                }
            }
        }

        Button(
            onClick = onClick,
            modifier = modifier,
            colors = colors,
            border = border,
            enabled = isEnabled,
            elevation = elevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp
            ),
            shape = shape,
            contentPadding = cp
        ) {
            Box(modifier = Modifier
                .addIf(size.isSpecified) { Modifier.size(size) }
                .addIf(size.isUnspecified) { Modifier.measured { size = it } }
                .width(IntrinsicSize.Max)
                .height(IntrinsicSize.Min)
            ) {
            Crossfade(contentState) { state ->
                when (state) {
                    ButtonContentState.Content -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            ProvideTextStyle(value = style) {
                                this@Button.content()
                            }
                        }
                    }

                    ButtonContentState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            CodeCircularProgressIndicator(
                                strokeWidth = CodeTheme.dimens.thickBorder,
                                color = White,
                                modifier = Modifier
                                    .size(CodeTheme.dimens.grid.x3)
                            )
                        }
                    }

                    ButtonContentState.Successful -> {
                        Icon(
                            modifier = Modifier.requiredSize(CodeTheme.dimens.grid.x3),
                            painter = painterResource(id = R.drawable.ic_check),
                            tint = CodeTheme.colors.success,
                            contentDescription = "",
                        )
                    }
                }
            }
                }
        }
    }
}

sealed interface ButtonContentState {
    data object Loading : ButtonContentState
    data object Successful : ButtonContentState
    data object Content : ButtonContentState
}

@Composable
fun getRipple(
    buttonState: ButtonState,
) = ripple(
    bounded = true,
    color = when (buttonState) {
        ButtonState.Bordered -> White
        ButtonState.Filled -> CodeTheme.colors.brandLight
        ButtonState.Filled50 -> White50
        ButtonState.Subtle -> White
    }
)

@Composable
fun getButtonColors(
    enabled: Boolean,
    buttonState: ButtonState = ButtonState.Bordered,
    textColor: Color = Color.Unspecified,
): ButtonColors {
    return when (buttonState) {
        ButtonState.Filled -> ButtonDefaults.buttonColors(
            backgroundColor = White,
            contentColor = textColor.takeOrElse { Color(0XFF121212) },
            disabledBackgroundColor = White10,
            disabledContentColor = White10,
        )

        ButtonState.Bordered ->
            ButtonDefaults.outlinedButtonColors(
                backgroundColor = Transparent,
                disabledContentColor = Color.White.copy(0.30f),
                contentColor = textColor.takeOrElse { Color.LightGray }
            )

        ButtonState.Filled50 ->
            ButtonDefaults.outlinedButtonColors(
                backgroundColor = White50,
                disabledContentColor = White50,
                contentColor = textColor.takeOrElse { White },
            )

        ButtonState.Subtle ->
            ButtonDefaults.outlinedButtonColors(
                backgroundColor = Transparent,
                disabledContentColor = Transparent,
                contentColor = if (enabled) textColor.takeOrElse { CodeTheme.colors.brandLight } else Color.White.copy(
                    0.30f
                )
            )
    }
}

@Composable
fun getButtonBorder(buttonState: ButtonState, isEnabled: Boolean = true): BorderStroke {
    val border = CodeTheme.dimens.border
    return remember(buttonState, isEnabled) {
        when (buttonState) {
            ButtonState.Bordered -> BorderStroke(border, if (isEnabled) White50 else White20)
            else -> BorderStroke(border, Transparent)
        }
    }
}
