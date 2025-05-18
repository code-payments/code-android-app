package com.getcode.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.getcode.view.shapes.TriangleCutShape

internal val shapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(15.dp)
)

val Shapes.extraSmall: CornerBasedShape
    @Composable get() = RoundedCornerShape(6.dp)

val Shapes.extraLarge: CornerBasedShape
    @Composable get() = RoundedCornerShape(20.dp)

val Shapes.xxl: CornerBasedShape
    @Composable get() = RoundedCornerShape(25.dp)

@Composable
fun Shapes.receipt(step: Dp = CodeTheme.dimens.grid.x2) = TriangleCutShape(step)

