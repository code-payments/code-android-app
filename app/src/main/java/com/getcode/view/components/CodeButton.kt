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
    shape: Shape = MaterialTheme.shapes.small,
) {
    val isEnabledC = enabled && !isLoading && !isSuccess
    Button(
        onClick = { onClick() },
        modifier = modifier
            .let { if (isMaxWidth) it.fillMaxWidth() else it }
            .padding(vertical = if (isPaddedVertical) 10.dp else 0.dp),
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
                style = MaterialTheme.typography.button,
                modifier = Modifier.padding(
                    vertical = if (isPaddedVertical) 14.dp else 0.dp,
                ),
            )

            Row {
                if (isLoading) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = White,
                        modifier = Modifier
                            .padding(vertical = if (isPaddedVertical) 12.dp else 0.dp)
                            .size(20.dp)
                            .align(Alignment.CenterVertically)
                    )
                } else {
                    if (isSuccess || isTextSuccess) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_check),
                            contentDescription = "",
                            modifier = Modifier
                                .padding(
                                    horizontal = 5.dp,
                                    vertical = if (isPaddedVertical) 14.dp else 0.dp
                                )
                                .align(Alignment.CenterVertically)
                        )
                    }
                    if (!isSuccess) {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.button,
                            modifier = Modifier.padding(
                                vertical = if (isPaddedVertical) 14.dp else 0.dp,
                                horizontal = 10.dp
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

fun getButtonBorder(buttonState: ButtonState, isEnabled: Boolean = true): BorderStroke? {
    return if (buttonState == ButtonState.Bordered && isEnabled) {
        BorderStroke(1.dp, White50)
    } else {
        BorderStroke(1.dp, Color.Transparent)
    }
}
