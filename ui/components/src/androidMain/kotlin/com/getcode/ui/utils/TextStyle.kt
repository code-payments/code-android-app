package com.getcode.ui.utils

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle

fun TextStyle.withDropShadow(
    color: Color = Color.Black.copy(alpha = 0.5f),
    offset: Offset = Offset(4f, 4f),
    blurRadius: Float = 4f,
): TextStyle = copy(
    shadow = Shadow(
        color = color,
        offset = offset,
        blurRadius = blurRadius
    )
)