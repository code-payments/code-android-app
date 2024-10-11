package com.getcode.oct24.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.getcode.theme.Alert
import com.getcode.theme.BrandLight
import com.getcode.theme.BrandMuted
import com.getcode.theme.BrandOverlay
import com.getcode.theme.ColorScheme
import com.getcode.theme.DesignSystem
import com.getcode.theme.Error
import com.getcode.theme.Gray50
import com.getcode.theme.TextMain
import com.getcode.theme.White

private val FC_Primary = Color(0xFF362774)
private val FC_Secondary = Color(0xFF443091)
private val FC_Tertiary = Color(0xFF7D6CC3)
private val FC_TextWithPrimary = Color(0xFFD2C6FF)
private val FC_Accent = Color(0xFFC372FF)

private val colors = ColorScheme(
    brand = FC_Primary,
    brandLight = BrandLight,
    brandSubtle = FC_Secondary,
    brandMuted = BrandMuted,
    brandDark = Color(0xFF2C2158),
    brandOverlay = BrandOverlay,
    brandContainer = FC_Primary,
    secondary = FC_Secondary,
    tertiary = FC_Tertiary,
    indicator = FC_Accent,
    action = Gray50,
    onAction = White,
    background = FC_Primary,
    onBackground = White,
    surface = Color(0xFF28176E),
    surfaceVariant = FC_Secondary,
    onSurface = White,
    error = Error,
    errorText = Alert,
    textMain = TextMain,
    textSecondary = FC_TextWithPrimary
)

@Composable
fun FlipchatTheme(content: @Composable () -> Unit) {
    DesignSystem(colorScheme = colors, content = content)
}