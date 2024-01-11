package com.getcode.view.components

import android.view.MotionEvent
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.ripple.LocalRippleTheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material.ripple.RippleTheme
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.getcode.R
import com.getcode.theme.*
import java.text.DecimalFormatSymbols


@Composable
fun CodeKeyPad(
    modifier: Modifier = Modifier,
    onNumber: (number: Int) -> Unit,
    onDecimal: () -> Unit = {},
    onClear: () -> Unit,
    isDecimal: Boolean = false
) {
    Row(
        modifier = modifier
            .padding(horizontal = CodeTheme.dimens.grid.x4)
            .widthIn(100.dp, 350.dp),
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1)
    ) {
        for (column in 1..3) {
            Column(
                modifier = Modifier
                    .weight(1f)
            ) {
                for (row in 1..4) {
                    val number = (row - 1) * 3 + column

                    if (row <= 3) {
                        NumberButton(
                            number = number
                        ) { onNumber(number) }
                    } else {
                        when (column) {
                            1 -> if (isDecimal) {
                                KeyBoardButton(
                                    text = DecimalFormatSymbols.getInstance().decimalSeparator.toString(),
                                ) { onDecimal() }
                            }
                            2 -> NumberButton(
                                number = 0
                            ) { onNumber(0) }
                            3 -> KeyBoardButton(
                                resId = R.drawable.ic_chevron_left,
                            ) { onClear() }
                        }
                    }

                    Spacer(modifier = Modifier.height(getSpacerSize().dp))
                }
            }
        }
    }
}

@Composable
private fun NumberButton(
    number: Int,
    onClick: () -> Unit
) {
    KeyBoardButton(text = number.toString(), onClick = onClick)
    Spacer(modifier = Modifier.width(CodeTheme.dimens.grid.x2))
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun KeyBoardButton(
    text: String? = null,
    resId: Int? = null,
    onClick: () -> Unit
) {
    var buttonPressed by remember { mutableStateOf(false) }

    val transition = updateTransition(buttonPressed, label = "")
    val scale by transition.animateFloat(label = "") { isSelected ->
        if (!isSelected) 1f else 1.5f
    }
    val bgColor by transition.animateColor(label = "") { isSelected ->
        if (!isSelected) Transparent else White10
    }

    CompositionLocalProvider(LocalRippleTheme provides RippleCustomTheme) {
        OutlinedButton(
            modifier = Modifier
                .pointerInteropFilter {
                    when (it.action) {
                        MotionEvent.ACTION_DOWN -> {
                            buttonPressed = true
                        }
                        MotionEvent.ACTION_UP -> {
                            onClick()
                            buttonPressed = false
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            buttonPressed = false
                        }
                    }
                    buttonPressed
                },
            onClick = { buttonPressed = false },
            border = null,
            colors = ButtonDefaults.outlinedButtonColors(
                backgroundColor = bgColor,
                contentColor = White
            )
        ) {
            if (resId != null) {
                Image(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CodeTheme.dimens.staticGrid.x8)
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale
                        ),
                    painter = painterResource(id = resId),
                    contentDescription = ""
                )
            } else if (text != null) {
                Text(
                    text = text,
                    style = CodeTheme.typography.button.copy(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = CodeTheme.dimens.grid.x1)
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale
                        )
                )
            }
        }
    }
}


@Composable
private fun getSpacerSize(): Int {
    return when (CodeTheme.dimens.heightWindowSizeClass) {
        WindowSizeClass.COMPACT -> 0
        else -> 10
    }
}

private object RippleCustomTheme : RippleTheme {
    @Composable
    override fun defaultColor() =
        RippleTheme.defaultRippleColor(
            White,
            lightTheme = true
        )

    @Composable
    override fun rippleAlpha(): RippleAlpha =
        RippleTheme.defaultRippleAlpha(
            Brand,
            lightTheme = true
        )
}