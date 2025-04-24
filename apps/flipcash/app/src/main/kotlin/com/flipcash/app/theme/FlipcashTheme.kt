package com.flipcash.app.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.getcode.theme.BrandAccent
import com.getcode.theme.BrandDark
import com.getcode.theme.BrandIndicator
import com.getcode.theme.BrandLight
import com.getcode.theme.BrandMuted
import com.getcode.theme.BrandOverlay
import com.getcode.theme.BrandSlideToConfirm
import com.getcode.theme.BrandSubtle
import com.getcode.theme.ColorScheme
import com.getcode.theme.DesignSystem
import com.getcode.theme.Error
import com.getcode.theme.Gray50
import com.getcode.theme.Success
import com.getcode.theme.TextError
import com.getcode.theme.White
import com.getcode.theme.White05
import com.getcode.theme.White10
import com.getcode.theme.codeTypography

private object ColorSpec {
    val primary = Color(0xFF001A0C)
    val secondaryText = Color(115, 129, 121)
    val cashBill = Color(0xFF06450F)
}

private val colors = with(ColorSpec) {
    ColorScheme(
        brand = primary,
        brandLight = BrandLight,
        brandSubtle = BrandSubtle,
        brandMuted = BrandMuted,
        brandDark = BrandDark,
        brandOverlay = BrandOverlay,
        brandContainer = primary,
        secondary = BrandAccent,
        tertiary = BrandAccent,
        indicator = BrandIndicator,
        action = Gray50,
        onAction = White,
        background = primary,
        onBackground = White,
        surface = primary,
        surfaceVariant = BrandDark,
        onSurface = White,
        error = Error,
        errorText = TextError,
        success = Success,
        textMain = Color.White,
        textSecondary = secondaryText,
        divider = White10,
        dividerVariant = White05,
        trackColor = BrandSlideToConfirm,
        cashBill = cashBill,
    )
}

@Composable
fun FlipcashTheme(content: @Composable () -> Unit) {
    DesignSystem(
        colorScheme = colors,
        // override code type system to make screen title's slightly bigger
        typography = codeTypography.copy(
            screenTitle = codeTypography.displayExtraSmall.copy(fontWeight = FontWeight.W500)
        ),
        content = content
    )
}