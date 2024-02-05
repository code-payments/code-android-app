package com.getcode.util

import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable

@Suppress("UnusedReceiverParameter")
fun MeasureScope.widthOrZero(placeable: Placeable?): Int {
    return placeable?.measuredWidth ?: 0
}

@Suppress("UnusedReceiverParameter")
fun MeasureScope.heightOrZero(placeable: Placeable?): Int {
    return placeable?.measuredHeight ?: 0
}