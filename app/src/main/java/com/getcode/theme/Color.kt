package com.getcode.theme

import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.ui.graphics.Color

val Brand = Color(0xff0F0C1F)
val BrandLight = Color(0xFF7379A0)
val BrandSubtle = Color(0xFF565C86)

val Brand01 = Color(0xFF130F27)
val White = Color(0xffffffff)
val White50 = Color(0x80FFFFFF)
val White10 = Color(0x1AFFFFFF)
val White05 = Color(0x0CFFFFFF)
val Black10 = Color(0x19000000)
val Black40 = Color(0x66000000)
val Black50 = Color(0x80000000)
val Transparent = Color(0x00FFFFFF)

val Gray50 = Color(0x803C3C3C)

val Alert = Color(0xFFFF8383)
val TopError = Color(0xFFBC3434)
val topWarning = Color(0xFFf1ab1f)
val topInfo = Color(0xFF4f49ce)
val topNeutral = Color(0xFF747474)

val green = Color(0xFF87D300)

val textSelectionColors = TextSelectionColors(
    handleColor = White,
    backgroundColor = BrandLight.copy(alpha = 0.4f)
)