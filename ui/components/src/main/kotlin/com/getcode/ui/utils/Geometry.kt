package com.getcode.ui.utils

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import kotlin.math.hypot

open class Geometry(width: Dp, height: Dp) {
    var size by mutableStateOf(DpSize.Zero)
        private set

    open val codeSize: Dp
        get() = size.width * 0.65f

    val diagonalDistance: Float
        get() = hypot(size.width.value, size.height.value)

    init {
        size = DpSize(width, height)
    }
}