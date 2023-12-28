package com.getcode.util

import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

inline fun Modifier.addIf(
    predicate: Boolean,
    crossinline whenTrue: () -> Modifier,
): Modifier = if (predicate) {
    this.then(whenTrue())
} else {
    this
}


fun Modifier.debugBounds(color: Color = Color.Magenta, shape: Shape = RectangleShape) = this.border(1.dp, color, shape)


