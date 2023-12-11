package com.getcode.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
inline fun Modifier.conditionally(
    condition: Boolean,
    whenTrue: Modifier.() -> Modifier,
): Modifier {
    return if (condition) {
        whenTrue(this)
    } else {
        this
    }
}

