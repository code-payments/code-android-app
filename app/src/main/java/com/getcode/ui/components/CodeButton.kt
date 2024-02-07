package com.getcode.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.ButtonDefaults.elevation
import androidx.compose.material.ripple.LocalRippleTheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material.ripple.RippleTheme
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.isUnspecified
import com.getcode.R
import com.getcode.theme.*
import com.getcode.ui.utils.addIf
import com.getcode.ui.utils.measured
import com.getcode.ui.utils.plus

enum class ButtonState {
    Bordered,
    Filled,
    Filled10,
    Subtle
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun CodeButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    text: String,
    isLoading: Boolean = false,
    isSuccess: Boolean = false,
    isTextSuccess: Boolean = false,
    enabled: Boolean = true,
    buttonState: ButtonState = ButtonState.Bordered,
    textColor: Color = Color.Unspecified,
    shape: Shape = CodeTheme.shapes.small,
) {
    val isEnabled by remember(enabled, isLoading, isSuccess) {
        derivedStateOf { enabled && !isLoading && !isSuccess }
    }
    val isSuccessful by remember(isSuccess, isTextSuccess) {
        derivedStateOf { isSuccess || isTextSuccess }
    }
    val colors = getButtonColors(buttonState, textColor)
    val border = getButtonBorder(buttonState, isEnabled)
    val ripple = getRipple(
        buttonState = buttonState,
        contentColor = colors.contentColor(enabled = isEnabled).value
    )

    CompositionLocalProvider(
        LocalMinimumInteractiveComponentEnforcement provides false,
        LocalRippleTheme provides ripple
    ) {

        var size by remember {
            mutableStateOf(DpSize.Unspecified)
        }

        Button(
            onClick = onClick,
            modifier = Modifier
                .addIf(size.isSpecified) { Modifier.size(size) }
                .addIf(size.isUnspecified) { Modifier.measured { size = it }}.then(modifier),
            colors = colors,
            border = border,
            enabled = isEnabled,
            elevation = elevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp
            ),
            shape = shape,
            contentPadding = ButtonDefaults.ContentPadding.plus(
                top = CodeTheme.dimens.grid.x3,
                bottom = CodeTheme.dimens.grid.x3,
            )
        ) {
            when {
                isLoading -> {
                    CodeCircularProgressIndicator(
                        strokeWidth = CodeTheme.dimens.thickBorder,
                        color = White,
                        modifier = Modifier
                            .size(CodeTheme.dimens.grid.x3)
                    )
                }

                isSuccessful -> {
                    Icon(
                        modifier = Modifier.requiredSize(CodeTheme.dimens.grid.x3),
                        painter = painterResource(id = R.drawable.ic_check),
                        tint = Color.Unspecified,
                        contentDescription = "",
                    )
                }

                else -> {
                    Text(
                        text = text,
                        style = CodeTheme.typography.button,
                    )
                }
            }
        }
    }
}

@Composable
fun getRipple(
    buttonState: ButtonState,
    contentColor: Color
): RippleTheme {
    return remember(buttonState, contentColor) {
        object : RippleTheme {
            @Composable
            override fun defaultColor(): Color {
                return when (buttonState) {
                    ButtonState.Bordered -> White
                    ButtonState.Filled -> BrandLight
                    ButtonState.Filled10 -> White50
                    ButtonState.Subtle -> White
                }
            }

            @Composable
            override fun rippleAlpha(): RippleAlpha {
                return RippleTheme.defaultRippleAlpha(
                    contentColor,
                    lightTheme = true
                )
            }
        }
    }
}

@Composable
fun getButtonColors(
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
                backgroundColor = Brand,
                disabledContentColor = Color.LightGray,
                contentColor = textColor.takeOrElse { Color.LightGray }
            )

        ButtonState.Filled10 ->
            ButtonDefaults.outlinedButtonColors(
                backgroundColor = White10,
                disabledContentColor = White50,
                contentColor = textColor.takeOrElse { White50 },
            )

        ButtonState.Subtle ->
            ButtonDefaults.outlinedButtonColors(
                backgroundColor = Transparent,
                disabledContentColor = Transparent,
                contentColor = textColor.takeOrElse { BrandLight },
            )
    }
}

@Composable
fun getButtonBorder(buttonState: ButtonState, isEnabled: Boolean = true): BorderStroke? {
    val border = CodeTheme.dimens.border
    return remember(buttonState, isEnabled) {
        if (buttonState == ButtonState.Bordered && isEnabled) {
            BorderStroke(border, White50)
        } else {
            BorderStroke(border, Transparent)
        }
    }
}
