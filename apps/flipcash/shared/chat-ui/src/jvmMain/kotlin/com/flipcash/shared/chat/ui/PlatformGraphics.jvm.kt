package com.flipcash.shared.chat.ui

import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.skiaPaint
import androidx.compose.ui.text.PlatformTextStyle
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.MaskFilter

internal actual fun Paint.blurEdges(radiusPx: Float) {
    // Skia takes a Gaussian sigma; Android's radius is about twice it.
    skiaPaint.maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, radiusPx / 2f)
}

internal actual fun noFontPaddingStyle(): PlatformTextStyle? = null
