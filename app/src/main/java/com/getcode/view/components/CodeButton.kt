package com.getcode.view.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.ButtonDefaults.elevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.getcode.R
import com.getcode.theme.*

enum class ButtonState {
    Bordered,
    Filled,
    Filled10,
    Subtle
}

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
    isPaddedVertical: Boolean = true,
    textColor: Color? = null,
    isMaxWidth: Boolean = true,
    shape: Shape = CodeTheme.shapes.small,
) {
    val isEnabledC = enabled && !isLoading && !isSuccess
    Button(
        onClick = { onClick() },
        modifier = modifier
            .let { if (isMaxWidth) it.fillMaxWidth() else it },
        colors = getButtonColors(buttonState, textColor),
        border = getButtonBorder(buttonState, isEnabledC),
        enabled = isEnabledC,
        elevation = elevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp
        ),
        shape = shape,
    ) {
        Box {
            Text(
                text = " ",
                style = CodeTheme.typography.button,
                modifier = Modifier.padding(
                    vertical = if (isPaddedVertical) CodeTheme.dimens.grid.x3 else 0.dp,
                ),
            )

            Row {
                if (isLoading) {
                    CodeCircularProgressIndicator(
                        strokeWidth = CodeTheme.dimens.thickBorder,
                        color = White,
                        modifier = Modifier
                            .padding(vertical = if (isPaddedVertical) CodeTheme.dimens.grid.x3 else 0.dp)
                            .size(CodeTheme.dimens.grid.x4)
                            .align(Alignment.CenterVertically)
                    )
                } else {
                    if (isSuccess || isTextSuccess) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_check),
                            contentDescription = "",
                            modifier = Modifier
                                .padding(
                                    horizontal = CodeTheme.dimens.grid.x1,
                                    vertical = if (isPaddedVertical) CodeTheme.dimens.grid.x3 else 0.dp
                                )
                                .align(Alignment.CenterVertically)
                        )
                    }
                    if (!isSuccess) {
                        Text(
                            text = text,
                            style = CodeTheme.typography.button,
                            modifier = Modifier.padding(
                                vertical = if (isPaddedVertical) CodeTheme.dimens.grid.x3 else 0.dp,
                                horizontal = CodeTheme.dimens.grid.x2,
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun getButtonColors(
    buttonState: ButtonState = ButtonState.Bordered,
    textColor: Color? = null,
): ButtonColors {
    return when (buttonState) {
        ButtonState.Filled -> ButtonDefaults.buttonColors(
            backgroundColor = Color.White,
            contentColor = textColor ?: Color(0XFF121212),
            disabledBackgroundColor = White10,
            disabledContentColor = White10,
        )
        ButtonState.Bordered ->
            ButtonDefaults.outlinedButtonColors(
                backgroundColor = Brand,
                disabledContentColor = Color.LightGray,
                contentColor = textColor ?: Color.LightGray,
            )
        ButtonState.Filled10 ->
            ButtonDefaults.outlinedButtonColors(
                backgroundColor = White10,
                disabledContentColor = White50,
                contentColor = textColor ?: White50,
            )
        ButtonState.Subtle ->
            ButtonDefaults.outlinedButtonColors(
                backgroundColor = Transparent,
                disabledContentColor = Transparent,
                contentColor = textColor ?: BrandLight,
            )
    }
}

@Composable
fun getButtonBorder(buttonState: ButtonState, isEnabled: Boolean = true): BorderStroke? {
    return if (buttonState == ButtonState.Bordered && isEnabled) {
        BorderStroke(CodeTheme.dimens.border, White50)
    } else {
        BorderStroke(CodeTheme.dimens.border, Color.Transparent)
    }
}
