package com.getcode.ui.utils

import androidx.compose.ui.unit.TextUnit

fun TextUnit.coerceAtMost(other: TextUnit): TextUnit {
    return if (this > other) other else this
}

fun TextUnit.coerceAtLeast(other: TextUnit): TextUnit {
    return if (this < other) other else this
}

fun TextUnit.coerceIn(min: TextUnit, max: TextUnit): TextUnit {
    return coerceAtLeast(min).coerceAtMost(max)
}