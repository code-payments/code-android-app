package com.getcode.ui.components.chat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.getcode.theme.CodeTheme
import com.getcode.theme.DesignSystem
import kotlinx.coroutines.delay

private const val StepDuration = 400

@Composable
fun TypingIndicator(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(
                color = CodeTheme.colors.brandDark,
                shape = CodeTheme.shapes.small
            )
            .padding(CodeTheme.dimens.grid.x2),
        horizontalArrangement = Arrangement.spacedBy(
            CodeTheme.dimens.grid.x1,
            Alignment.CenterHorizontally
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val baseColor = CodeTheme.colors.secondary

        val alphaValues = remember { arrayOf(1f, 0.60f, 0.20f) }
        var currentIndex by remember { mutableIntStateOf(0) }

        val animatedAlphas = List(alphaValues.size) { index ->
            val targetAlpha =
                if (index == currentIndex) 1f else alphaValues[(index - currentIndex + 3) % 3]
            animateFloatAsState(
                targetValue = targetAlpha,
                animationSpec = tween(durationMillis = StepDuration),
                label = ""
            )
        }

        val scales = List(alphaValues.size) { index ->
            val targetScale = if (index == currentIndex) 1f else 0.8f
            animateFloatAsState(
                targetValue = targetScale,
                animationSpec = tween(durationMillis = StepDuration),
                label = ""
            )
        }

        LaunchedEffect(Unit) {
            while (true) {
                delay(StepDuration.toLong())
                currentIndex = (currentIndex + 1) % 3
            }
        }


        animatedAlphas.zip(scales).forEach { (alpha, scale) ->
            Box(
                modifier = Modifier
                    .size(CodeTheme.dimens.grid.x2)
                    .scale(scale.value)
                    .background(
                        color = baseColor.copy(alpha = alpha.value),
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
@Preview
fun PreviewTypingIndicator() {
    DesignSystem {
        Box(modifier = Modifier.size(400.dp), contentAlignment = Alignment.Center) {
            TypingIndicator()
        }
    }
}