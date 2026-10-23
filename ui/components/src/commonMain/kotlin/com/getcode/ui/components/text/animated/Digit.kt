package com.getcode.ui.components.text.animated

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import com.getcode.theme.CodeTheme

@Composable
internal fun Digit(
    isVisible: Boolean,
    text: String,
    fontSize: TextUnit,
    density: Density,
    modifier: Modifier = Modifier,
    enter: EnterTransition? = null,
    exit: ExitTransition? = null,
    color: Color = Color.White,
) {
    val staticX4 = CodeTheme.dimens.staticGrid.x4
    val defaultEnter = defaultDigitEnter(initialOffsetY = with(density) { -(staticX4).roundToPx() })
    val enterTransition = remember(enter) {
        enter ?: defaultEnter
    }

    val defaultExit = defaultDigitExit(targetOffsetY = with(density) { -(staticX4).roundToPx() })
    val exitTransition = remember(exit) {
        exit ?: defaultExit
    }

    Row(
        modifier = modifier,
    ) {
        AnimatedContent(
            targetState = isVisible,
            transitionSpec = { enterTransition togetherWith exitTransition }
        ) { visible ->
            if (visible) {
                Text(
                    text = text,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            } else {
                Spacer(modifier = Modifier)
            }
        }
    }
}