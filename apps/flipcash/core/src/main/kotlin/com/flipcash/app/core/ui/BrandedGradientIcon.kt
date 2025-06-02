package com.flipcash.app.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import com.getcode.theme.CodeTheme

@Composable
fun BrandedGradientIcon(
    painter: Painter,
    modifier: Modifier = Modifier,
) {
    Image(
        modifier = Modifier
            .size(120.dp)
            .background(
                brush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0.43f to Color(0xFF0B2D17),
                        0.67f to Color(0xFF053209),
                    ),
                ),
                shape = CircleShape
            )
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(42,92,34),
                            Color(0x00FFFFFF),
                        ),
                        center = Offset(size.width * 0.25f, size.height * 0.25f),
                        radius = size.width * 0.5f,
                    ),
                    radius = size.width / 2,
                )
            }
            .padding(CodeTheme.dimens.grid.x6)
            .then(modifier),
        painter = painter,
        colorFilter = ColorFilter.tint(Color.White),
        contentDescription = null,
    )
}