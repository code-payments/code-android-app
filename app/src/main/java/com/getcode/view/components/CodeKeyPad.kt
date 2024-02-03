package com.getcode.view.components

import android.view.MotionEvent
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.LocalContentColor
import androidx.compose.material.Text
import androidx.compose.material.ripple.LocalRippleTheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material.ripple.RippleTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.getcode.R
import com.getcode.theme.Brand
import com.getcode.theme.CodeTheme
import com.getcode.theme.Transparent
import com.getcode.theme.White
import com.getcode.theme.White10
import com.getcode.util.rememberedClickable
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
        modifier = Modifier
            .widthIn(min = 100.dp)
            .then(modifier),
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
        verticalAlignment = Alignment.Bottom
    ) {
        for (column in 1..3) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1)
            ) {
                for (row in 1..4) {
                    val number = (row - 1) * 3 + column

                    if (row <= 3) {
                        NumberButton(
                            modifier = Modifier.weight(1f),
                            number = number
                        ) { onNumber(number) }
                    } else {
                        when (column) {
                            1 -> if (isDecimal) {
                                KeyBoardButton(
                                    modifier = Modifier.weight(1f),
                                    text = DecimalFormatSymbols.getInstance().decimalSeparator.toString(),
                                ) { onDecimal() }
                            }
                            2 -> NumberButton(
                                modifier = Modifier.weight(1f),
                                number = 0
                            ) { onNumber(0) }
                            3 -> KeyBoardButton(
                                modifier = Modifier.weight(1f),
                                resId = R.drawable.ic_chevron_left,
                            ) { onClear() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NumberButton(
    modifier: Modifier = Modifier,
    number: Int,
    onClick: () -> Unit
) {
    KeyBoardButton(modifier = modifier, text = number.toString(), onClick = onClick)
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun KeyBoardButton(
    modifier: Modifier = Modifier,
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
        Box(
            modifier = modifier
                .background(bgColor)
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
                }
                .rememberedClickable { buttonPressed = false },
            contentAlignment = Alignment.Center
        ) {
            CompositionLocalProvider(LocalContentColor provides Color.White) {
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
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = text,
                            style = CodeTheme.typography.button.copy(
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Normal,
                                textAlign = TextAlign.Center
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = CodeTheme.dimens.staticGrid.x1)
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale
                                )
                        )
                    }
                }
            }
        }
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