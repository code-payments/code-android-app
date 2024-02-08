package com.getcode.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.chat.ChatNodeDefaults
import com.getcode.ui.utils.circleBackground


@Composable
fun Badge(
    modifier: Modifier = Modifier,
    count: Int,
    color: Color = CodeTheme.colors.brand,
    contentColor: Color = Color.White,
) {
    AnimatedVisibility(visible = count > 0, enter = scaleIn() + fadeIn(), exit = fadeOut() + scaleOut()) {
        val text = when {
            count in 1..99 -> "$count"
            else -> "99+"
        }

        Text(
            text = text,
            color = contentColor,
            style = CodeTheme.typography.body1.copy(fontWeight = FontWeight.W700),
            modifier = modifier
                .drawBehind {
                    drawCircle(
                        color = color,
                        radius = this.size.maxDimension / 2f
                    )
                }.padding(2.dp)
        )
    }
}