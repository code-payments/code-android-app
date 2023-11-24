package com.getcode.view.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.getcode.theme.BrandLight
import com.getcode.util.WindowSize

@Composable
fun OtpBox(
    character: String,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false,
    onClick: () -> Unit,
) {
    val windowSize = windowSizeCheck()

    val height = when (windowSize) {
        WindowSize.SMALL -> 45
        WindowSize.REGULAR -> 60
    }
    val width = when (windowSize) {
        WindowSize.SMALL -> 30
        WindowSize.REGULAR -> 42
    }

    Box(
        modifier = modifier
            .padding(5.dp)
            .height(height.dp)
            .width(width.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .border(
                border = if (isHighlighted)
                    BorderStroke(2.dp, color = BrandLight.copy(alpha = 0.8f))
                else
                    BorderStroke(1.dp, color = BrandLight.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(8.dp)
            )
            .background(Color.White.copy(alpha = 0.1f)),
    ) {
        Text(
            text = character,
            modifier = Modifier
                .align(Alignment.Center),
            style = MaterialTheme.typography.h6.copy(fontWeight = FontWeight.Normal),
            color = Color.White,
        )
    }
}