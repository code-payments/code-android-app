package com.getcode.theme

import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.ui.graphics.Color

val Brand = Color(0xff0F0C1F)
val BrandLight = Color(0xFF7379A0)
val BrandSubtle = Color(0xFF565C86)
val BrandMuted = Color(0xFF45464E)
val BrandDark = Color(0xFF1F1A34)
val BrandAction = Color(0xFF212121)

val Brand01 = Color(0xFF130F27)
val White = Color(0xffffffff)
val White50 = Color(0x80FFFFFF)
val White10 = Color(0x1AFFFFFF)
val White20 = Color(0x33FFFFFF)
val White05 = Color(0x0CFFFFFF)
val Black10 = Color(0x19000000)
val Black40 = Color(0x66000000)
val Black50 = Color(0x80000000)
val Transparent = Color(0x00FFFFFF)
val Gray50 = Color(0x803C3C3C)
val DashEffect = Color(0xFF303137)

val TextMain = White
val TextSecondary = BrandLight
val TextError = Color(0xFFDD8484)

val Alert = Color(0xFFFF8383)
val Warning = Color(0xFFf1ab1f)
val Success = Color(0xFF87D300)
val Error = Color(0xFFA42D2D)

val SystemGreen = Color(0xFF04C759)

val ChatOutgoing = Color(0xFF443091)

val TopNotification = Color(0xFF4f49ce)
val TopNeutral = Color(0xFF747474)
val TopSuccess = Brand

val textSelectionColors = TextSelectionColors(
    handleColor = White,
    backgroundColor = BrandLight.copy(alpha = 0.4f)
)
