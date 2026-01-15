package com.getcode.ui.components.text

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.getcode.ui.components.text.animated.defaultDigitEnter
import com.getcode.ui.components.text.animated.defaultDigitExit

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
        ) {
            AnimatedContent(
                targetState = char,
                transitionSpec = {
                    if (targetState != initialState) {
                        // Enter: slide in from bottom (increasing) or top (decreasing) + fade
                        val enter = slideInVertically { height ->
                            if (direction > 0) height else -height
                        } + fadeIn()

                        // Exit: just fade/blur in place - no slide
                        val exit = fadeOut()

                        enter togetherWith exit using SizeTransform()
                    } else {
                        EnterTransition.None togetherWith ExitTransition.None
                    }
                },
                label = "digit"
            ) { char ->
                // Apply blur during exit transition
                val blurRadius by transition.animateFloat(
                    transitionSpec = { tween(150) },
                    label = "blur"
                ) { state ->
                    when (state) {
                        EnterExitState.PreEnter -> 12f
                        EnterExitState.Visible -> 0f
                        EnterExitState.PostExit -> 12f
                    }
                }

                Text(
                    modifier = Modifier.blur(blurRadius.dp),
                    text = char.toString(),
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