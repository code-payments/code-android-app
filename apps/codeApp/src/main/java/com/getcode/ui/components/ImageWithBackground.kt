package com.getcode.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource

@Composable
fun ImageWithBackground(
    modifier: Modifier = Modifier,
    painter: Painter,
    @DrawableRes backgroundDrawableResId: Int,
    contentDescription: String?,
    shape: Shape = RectangleShape,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    imageScale: Float = 1f,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null
) {
    Box(
        modifier = modifier.background(color = Color.Unspecified, shape = shape),
    ) {
        Image(
            painter = painter,
            contentDescription = contentDescription,
            alignment = alignment,
            contentScale = contentScale,
            alpha = alpha,
            colorFilter = colorFilter,
            modifier = Modifier.scale(imageScale),
        )
        Image(
            modifier = Modifier
                .matchParentSize()
                .clip(shape),
            alignment = alignment,
            contentScale = contentScale,
            alpha = alpha,
            painter = painterResource(backgroundDrawableResId),
            contentDescription = null,
        )
    }
}