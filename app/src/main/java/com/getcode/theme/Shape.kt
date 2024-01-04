package com.getcode.theme

import android.graphics.drawable.shapes.Shape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

internal val shapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(15.dp)
)

val Shapes.extraSmall: CornerBasedShape
    @Composable get() = RoundedCornerShape(5.dp)

val Shapes.extraLarge: CornerBasedShape
    @Composable get() = RoundedCornerShape(20.dp)

val Shapes.xxl: CornerBasedShape
    @Composable get() = RoundedCornerShape(25.dp)