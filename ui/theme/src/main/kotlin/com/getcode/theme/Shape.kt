package com.getcode.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
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

/**
 * Returns a new [RoundedCornerShape] whose corners are concentric with this shape,
 * inset by [inset]. Useful for nested rounded rectangles (e.g. a thumb inside a track)
 * where the inner corners should run parallel to the outer ones.
 */
@Composable
fun CornerBasedShape.inset(inset: Dp): RoundedCornerShape {
    val density = LocalDensity.current
    // Use a large reference size so percentage-based CornerSizes resolve to absolute values.
    val ref = Size(10_000f, 10_000f)
    fun CornerSize.shrink(): Dp = max(0.dp, with(density) { toPx(ref, this).toDp() } - inset)
    return RoundedCornerShape(
        topStart = topStart.shrink(),
        topEnd = topEnd.shrink(),
        bottomEnd = bottomEnd.shrink(),
        bottomStart = bottomStart.shrink(),
    )
}

