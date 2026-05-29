package com.getcode.theme

import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.ui.graphics.Color
import com.getcode.ui.utils.toAGColor

val Brand = Color(0xFF0F0C1F)
val BrandLight = Color(0xFF7379A0)
val BrandSubtle = Color(0xFF565C86)
val BrandMuted = Color(0xFF45464E)
val BrandDark = Color(0xFF1F1A34)
val BrandAction = Color(0xFF212121)
val BrandOverlay = Color(0xBF1E1E1E)
val BrandAccent = Color(0xFF443091)
val BrandIndicator = Color(0xFF31BB00)
val BrandSlideToConfirm = Color(0xFF11142A)
val BrandToggleUncheckedTrackColor = Color(0xFF201D2F)

val White = Color(0xffffffff)
val White50 = Color(0x80FFFFFF)
val White40 = Color(0x66FFFFFF)
val White10 = Color(0x1AFFFFFF)
val White20 = Color(0x33FFFFFF)
val White05 = Color(0x0CFFFFFF)
val White08 = Color(0x14FFFFFF)
val Black10 = Color(0x19000000)
val Black40 = Color(0x66000000)
val Black50 = Color(0x80000000)
val Transparent = Color(0x00FFFFFF)
val Gray50 = Color(0x803C3C3C)
val DashEffect = Color(0xFF303137)

val Alert = Color(0xFFFF8383)
val Info = Color(0xFF565C86)
val Warning = Color(0xFFf1ab1f)
val Success = Color(0xFF87D300)
val BannerSuccess = Color(0xFF0F5020)
val Error = Color(0xFFA42D2D)

val CashBill = Color(red = 44, green = 42, blue = 65)
val CashBillDecor = Color(0xFFA9A9B1)

val BetaIndicator = Color(0xFFFEF383)

val TextMain = White
val TextSecondary = BrandLight
val TextTertiary = BrandMuted
val TextError = Alert

val SystemGreen = Color(0xFF04C759)

val TopNotification = Color(0xFF4f49ce)
val TopNeutral = Color(0xFF747474)

val textSelectionColors = TextSelectionColors(
    handleColor = White,
    backgroundColor = BrandLight.copy(alpha = 0.4f)
)

val CodeAccessKey = GradientSpec(
    background = Brand,
    colors = listOf(
        Color(0xFF1A1A2E),
        Color(0xFF2D3748),
        Color(0xFF4A5568),
        Color(0xFF6B7A90),
    ),
    stops = listOf(0f, 0.3f, 0.6f, 1f)
)

val CodeContactAvatar = GradientSpec(
    background = Brand,
    colors = listOf(
        Color(0xFF4A5568),
        Color(0xFF6B7A90),
    ),
    stops = listOf(0f, 1f)
)

data class GradientSpec(
    val background: Color,
    val colors: List<Color>,
    val stops: List<Float>,
    val borderColor: Color? = null,
    val borderWidth: Float = 0f,
) {
    val colorsArray = colors.map { it.toAGColor() }.toIntArray()
    val stopsArray = stops.toFloatArray()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GradientSpec) return false
        if (!colorsArray.contentEquals(other.colorsArray)) return false
        return stopsArray.contentEquals(other.stopsArray)
    }

    override fun hashCode(): Int {
        return 31 * colorsArray.contentHashCode() + stopsArray.contentHashCode()
    }
}