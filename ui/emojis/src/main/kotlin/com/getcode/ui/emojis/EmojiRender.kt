package com.getcode.ui.emojis

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.getcode.theme.CodeTheme

@Composable
fun EmojiRender(
    emoji: String,
    modifier: Modifier = Modifier,
    showBackground: Boolean = true,
    onClick: () -> Unit
) {
    val textMeasurer = rememberTextMeasurer()

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(
                width = CodeTheme.dimens.grid.x9,
                height = CodeTheme.dimens.grid.x8,
            )
            .clip(CircleShape)
            .clickable { onClick() }
            .background(
                color = if (showBackground) CodeTheme.colors.brandDark else Color.Transparent,
                shape = CircleShape
            )
    ) {

        val emojiText = if (LocalInspectionMode.current) {
            emoji
        } else {
            processEmoji(emoji).toString()
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val measuredText = textMeasurer.measure(
                text = emojiText,
                style = TextStyle(fontSize = 24.sp)
            )
            drawText(
                textLayoutResult = measuredText,
                topLeft = Offset(
                    x = (size.width - measuredText.size.width) / 2,
                    y = (size.height - measuredText.size.height) / 2
                ),
                color = Color.White
            )
        }
    }
}