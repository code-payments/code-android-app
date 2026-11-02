package com.flipcash.shared.chat.ui

import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.text.PlatformTextStyle

/** Blurs the edge of what this paint draws, as a soft shadow does. [radiusPx] is the blur radius. */
internal expect fun Paint.blurEdges(radiusPx: Float)

/** Text without Android's extra font padding above and below the line; null where there is none. */
internal expect fun noFontPaddingStyle(): PlatformTextStyle?
