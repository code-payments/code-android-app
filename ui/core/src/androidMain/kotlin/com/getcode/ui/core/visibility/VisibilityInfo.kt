package com.getcode.ui.core.visibility

import androidx.compose.ui.geometry.Rect

data class VisibilityInfo(
    val isVisible: Boolean,
    val visiblePercentage: Float,
    val bounds: Rect,
    val isAboveThreshold: Boolean
)