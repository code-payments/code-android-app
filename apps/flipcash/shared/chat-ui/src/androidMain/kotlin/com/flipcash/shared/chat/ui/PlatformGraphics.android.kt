package com.flipcash.shared.chat.ui

import android.graphics.BlurMaskFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.text.PlatformTextStyle

internal actual fun Paint.blurEdges(radiusPx: Float) {
    asFrameworkPaint().maskFilter = BlurMaskFilter(radiusPx, BlurMaskFilter.Blur.NORMAL)
}

internal actual fun noFontPaddingStyle(): PlatformTextStyle? = PlatformTextStyle(includeFontPadding = false)
