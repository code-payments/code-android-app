package com.getcode.ui.utils

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize

open class Geometry(width: Dp, height: Dp) {
    var size by mutableStateOf(DpSize.Zero)
        private set

    init {
        size = DpSize(width, height)
    }
}