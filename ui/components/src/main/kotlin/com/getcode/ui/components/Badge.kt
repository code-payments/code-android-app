package com.getcode.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.getcode.theme.CodeTheme


@Composable
fun Badge(
    modifier: Modifier = Modifier,
    count: Int,
    showMoreUnread: Boolean = count > 99,
    color: Color = CodeTheme.colors.brand,
    contentColor: Color = Color.White,
    textStyle: TextStyle = CodeTheme.typography.textMedium.copy(fontWeight = FontWeight.W700),
    enterTransition: EnterTransition = scaleIn(tween(durationMillis = 300)) + fadeIn(),
    exitTransition: ExitTransition = fadeOut() + scaleOut(tween(durationMillis = 300))
) {
    AnimatedVisibility(
        modifier = modifier,
        visible = count > 0,
        enter = enterTransition,
        exit = exitTransition
    ) {
        val text = when {
            count == 0 -> ""
            showMoreUnread -> "$count+"
            else -> "$count"
        }

        Pill(
            backgroundColor = color,
            contentColor = contentColor,
            contentPadding = PaddingValues(
                horizontal = CodeTheme.dimens.grid.x1,
                vertical = 0.dp
            )
        ) {
            Text(
                text = text,
                style = textStyle,
            )
        }
    }
}