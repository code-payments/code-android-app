package com.flipcash.app.bill.customization.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.features.bill.playground.R
import com.getcode.theme.CodeTheme
import com.getcode.theme.extraSmall
import androidx.compose.foundation.clickable

@Composable
internal fun HueControlButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .width(CodeTheme.dimens.grid.x10)
            .fillMaxHeight()
            .rainbowBackground()
            .clip(CodeTheme.shapes.small)
            .clickable { onClick() }
            .padding(CodeTheme.dimens.thickBorder)
            .background(
                color = Color.Black.copy(0.50f),
                shape = CodeTheme.shapes.extraSmall
            )
            .padding(CodeTheme.dimens.grid.x3),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_color_tune_hsl),
            contentDescription = "Color manipulation",
            tint = Color.White
        )
    }
}

private fun Modifier.rainbowBackground(): Modifier = composed {
    val small = CodeTheme.shapes.small
    val thickBorder = CodeTheme.dimens.thickBorder
    val density = LocalDensity.current
    this.drawWithContent {
        drawRoundRect(
            brush = Brush.sweepGradient(
                colorStops = arrayOf( // Starts at 3 o'clock
                    0f to Color(0xFFBB3DFF),     // Purple starts at 3 o'clock (right)
                    0.16f to Color(0xFFFF3D3D),  // Dark red
                    0.22f to Color(0xFFFF7070),  // Light red
                    0.38f to Color(0xFFFFC23D),  // Yellow
                    0.58f to Color(0xFF54FF3D),  // Green
                    0.81f to Color(0xFF3DEFFF),  // Cyan
                    0.92f to Color(0xFF3DA8FF),  // Blue
                    1f to Color(0xFFBB3DFF)      // Loops back to purple
                ),
                center = center
            ),
            cornerRadius = CornerRadius(
                small.topStart.toPx(size, density),
                small.topEnd.toPx(size, density)
            ),
        )
        drawRoundRect(
            brush = SolidColor(Color.Black.copy(0.50f)),
            cornerRadius = CornerRadius(
                small.topStart.toPx(size, density),
                small.topEnd.toPx(size, density)
            ),
            topLeft = Offset(
                x = thickBorder.toPx(),
                y = thickBorder.toPx()
            ),
            size = size.copy(
                width = size.width - thickBorder.toPx() * 2,
                height = size.height - thickBorder.toPx() * 2
            )
        )
        drawContent()
    }
}

@Composable
@Preview
private fun HueControl_Preview() {
    FlipcashPreview {
        Box(modifier = Modifier.height(114.dp)) {
            HueControlButton { }
        }
    }
}