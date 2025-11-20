package com.flipcash.app.bill.customization.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flipcash.app.theme.FlipcashDesignSystem
import com.getcode.theme.CodeTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AlphaSlider(
    opacity: Float,
    onOpacityChange: (opacity: Float, isDragging: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    trackHeight: Dp = CodeTheme.dimens.grid.x2,
    thumbDiameter: Dp = 24.dp,
) {
    Slider(
        track = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .clip(CircleShape)
                    .drawBehind { drawOpacityTrack(size) }
            )
        },
        thumb = {
            Box(
                modifier = Modifier
                    .size(thumbDiameter)
                    .background(Color.Black.copy(0.25f), CircleShape)
                    .border(2.dp, Color.White, CircleShape)
            )
        },
        value = opacity,
        onValueChange = {
            onOpacityChange(it, true)
        },
        onValueChangeFinished = { onOpacityChange(opacity, false) },
        modifier = modifier
    )
}

private fun DrawScope.drawOpacityTrack(size: androidx.compose.ui.geometry.Size) {
    val checkerSize = size.height / 2

    // Checkerboard (visible through transparent part)
    for (i in 0..(size.width / checkerSize).toInt() + 1) {
        for (j in 0..(size.height / checkerSize).toInt() + 1) {
            val isWhite = (i + j) % 2 == 0
            drawRect(
                color = if (isWhite) Color(0xFFFFFFFF) else Color(0xFFE5E5E5),
                topLeft = Offset(i * checkerSize, j * checkerSize),
                size = androidx.compose.ui.geometry.Size(checkerSize, checkerSize)
            )
        }
    }

    // Gradient: transparent → solid white
    drawRect(
        brush = Brush.horizontalGradient(
            0f to Color.White.copy(alpha = 0f),
            1f to Color.White
        )
    )

    drawRect(
        brush = Brush.horizontalGradient(
            0f to Color.Black.copy(alpha = 0.50f),
            1f to Color.Transparent
        )
    )
}

@Composable
@Preview
private fun Preview_AlphaSlider() {
    FlipcashDesignSystem {
        Box(modifier = Modifier.fillMaxWidth().background(CodeTheme.colors.background)) {
            AlphaSlider(
                opacity = 0.5f,
                onOpacityChange = { _, _ -> },
            )
        }
    }
}