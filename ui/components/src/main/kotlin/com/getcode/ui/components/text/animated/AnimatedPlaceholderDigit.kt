package com.getcode.ui.components.text.animated

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import com.getcode.theme.CodeTheme

@Composable
internal fun AnimatedPlaceholderDigit(
    text: String,
    placeholder: String,
    digitVisible: Boolean,
    placeholderVisible: Boolean,
    fontSize: TextUnit,
    density: Density,
    modifier: Modifier = Modifier,
    placeholderEnter: EnterTransition,
    placeholderExit: ExitTransition,
    placeholderColor: Color = Color.White.copy(alpha = .2f),
) {
    val targetState = when {
        digitVisible -> PlaceholderState.Digit
        placeholderVisible -> PlaceholderState.Placeholder
        else -> PlaceholderState.None
    }

    val staticX4 = CodeTheme.dimens.staticGrid.x4
    val digitEnter = defaultDigitEnter(initialOffsetY = with(density) { -(staticX4).roundToPx() })
    val digitExit = defaultDigitExit(targetOffsetY = with(density) { -(staticX4).roundToPx() })

    AnimatedContent(
        targetState = targetState,
        transitionSpec = {
            when {
                // Placeholder -> Digit: Placeholder exits, Digit enters
                initialState == PlaceholderState.Placeholder&& targetState == PlaceholderState.Digit -> {
                    digitEnter togetherWith placeholderExit
                }
                // Digit -> Placeholder: Digit exits, Placeholder enters
                initialState == PlaceholderState.Digit&& targetState == PlaceholderState.Placeholder -> {
                    placeholderEnter togetherWith digitExit
                }

                // None -> Placeholder: None exits, Placeholder enters
                initialState == PlaceholderState.None && targetState == PlaceholderState.Placeholder -> {
                    placeholderEnter togetherWith ExitTransition.None
                }

                else -> {
                    EnterTransition.None togetherWith ExitTransition.None
                }
            }
        },
        modifier = modifier
    ) { state ->
        when (state) {
            PlaceholderState.Digit -> {
                Text(
                    text = text,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            PlaceholderState.Placeholder -> {
                Text(
                    text = placeholder,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    color = placeholderColor
                )
            }

            PlaceholderState.None -> Unit
        }
    }
}

private enum class PlaceholderState {
    Digit, Placeholder, None
}