package com.getcode.ui.components.text

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer

@Composable
fun AnimatedNumberText(
    value: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    Row(modifier = modifier) {
        value.forEach { char ->
            AnimatedDigit(
                char = char,
                style = style,
                color = color,
            )
        }
    }
}

@Composable
private fun AnimatedDigit(
    char: Char,
    style: TextStyle,
    color: Color,
) {
    val textMeasurer = rememberTextMeasurer()
    val height = remember(style) {
        textMeasurer.measure("0", style).size.height
    }

    var previousChar by remember { mutableStateOf(char) }
    val currentDigit = if (char.isDigit()) char.digitToInt() else 0
    val previousDigit = if (previousChar.isDigit()) previousChar.digitToInt() else 0

    LaunchedEffect(char) {
        previousChar = char
    }

    val direction = when {
        currentDigit > previousDigit -> 1
        currentDigit < previousDigit -> -1
        else -> 0
    }

    if (char.isDigit()) {
        Box(
            modifier = Modifier
                .height(with(LocalDensity.current) { height.toDp() })
                .clipToBounds()
        ) {
            AnimatedContent(
                targetState = char,
                transitionSpec = {
                    slideInVertically { direction * it } + fadeIn() togetherWith
                            slideOutVertically { -direction * it } + fadeOut()
                },
                label = "digitRoll",
            ) { digit ->
                Text(
                    text = digit.toString(),
                    style = style,
                    color = color,
                )
            }
        }
    } else {
        Text(
            text = char.toString(),
            style = style,
            color = color,
        )
    }
}