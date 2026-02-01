package com.flipcash.app.theme.internal

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.getcode.theme.BannerSuccess
import com.getcode.theme.BetaIndicator
import com.getcode.theme.Black40
import com.getcode.theme.BrandAccent
import com.getcode.theme.BrandDark
import com.getcode.theme.BrandIndicator
import com.getcode.theme.BrandMuted
import com.getcode.theme.BrandOverlay
import com.getcode.theme.ColorScheme
import com.getcode.theme.DesignSystem
import com.getcode.theme.Error
import com.getcode.theme.GradientSpec
import com.getcode.theme.Gray50
import com.getcode.theme.TextError
import com.getcode.theme.Warning
import com.getcode.theme.White
import com.getcode.theme.White05
import com.getcode.theme.White08
import com.getcode.theme.White10
import com.getcode.theme.codeTypography

object Flipcash2ColorSpec {
    val primary = Color(0xFF19191A)
    val primaryLight = Color(0xFF09550B)
    val secondary = Color(115, 129, 121)
    val secondaryText = Color.White.copy(alpha = 0.5f)
    val cashBill = Color(0xFF06450F)
    val trackColor = Color.White.copy(alpha = 0.07f)
    val bannerThemed = Color(0xFF1A3125)
    val success = Color(0xFF1AC86A)
    val successText = Color(0xFF73EAA4)
    val surfaceVariant = Color.White.copy(alpha = 0.12f)
    val successSurface = Color(0x4A6FCF97)
    val errorSurface = Color(0x4AE75454)
    val accessKey = GradientSpec(
        colors = listOf(
            Color(0xFF001A0C),
            Color(0xFF042005),
            Color(0xFF0C291A),
            Color(0xFF004602),
            Color(0xFF004602),
        ),
        stops = listOf(0f, 0.08f, 0.25f, 0.45f, 1f)
    )
}

private val colors = with(Flipcash2ColorSpec) {
    ColorScheme(
        brand = primary,
        brandLight = primaryLight,
        brandSubtle = secondary,
        brandMuted = BrandMuted,
        brandDark = BrandDark,
        brandOverlay = BrandOverlay,
        brandContainer = primary,
        secondary = secondary,
        tertiary = BrandAccent,
        indicator = BrandIndicator,
        action = Gray50,
        onAction = White,
        background = primary,
        onBackground = White,
        surface = primary,
        surfaceVariant = surfaceVariant,
        surfaceSuccess = successSurface,
        surfaceError = errorSurface,
        onSurface = White,
        error = Error,
        errorText = TextError,
        success = success,
        successText = successText,
        textMain = Color.White,
        textSecondary = secondaryText,
        border = White08,
        divider = White10,
        dividerVariant = White05,
        trackColor = trackColor,
        toggleUncheckedTrackColor = secondary,
        cashBill = cashBill,
        cashBillDecorColor = Color.White.copy(0.60f),
        betaIndicator = BetaIndicator,
        bannerThemed = bannerThemed,
        bannerError = Error,
        bannerWarning = Warning,
        bannerSuccess = BannerSuccess,
        scrim = Black40,
        accessKey = accessKey
    )
}
@Composable
internal fun Flipcash2DesignSystem(content: @Composable () -> Unit) {
    DesignSystem(
        colorScheme = colors,
        // override code type system to make screen title's slightly bigger
        typography = codeTypography.copy(
            screenTitle = codeTypography.displayExtraSmall.copy(fontWeight = FontWeight.W500)
        ),
        content = content
    )
}
