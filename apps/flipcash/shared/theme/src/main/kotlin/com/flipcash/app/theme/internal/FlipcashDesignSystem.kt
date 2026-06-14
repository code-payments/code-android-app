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
import com.getcode.theme.Bubble
import com.getcode.theme.ChatColors
import com.getcode.theme.ColorScheme
import com.getcode.theme.DesignSystem
import com.getcode.theme.Error
import com.getcode.theme.GradientSpec
import com.getcode.theme.Gray50
import com.getcode.theme.TextError
import com.getcode.theme.TypingIndicator
import com.getcode.theme.Warning
import com.getcode.theme.White
import com.getcode.theme.White05
import com.getcode.theme.White08
import com.getcode.theme.White10
import com.getcode.theme.codeTypography

object Flipcash2ColorSpec {
    val primary = Color(0xFF19191A)
    val primaryLight = Color(0xFF303031)
    val secondary = Color(115, 129, 121)
    val secondaryText = Color.White.copy(alpha = 0.5f)
    val cashBill = Color(0xFF06450F)
    val notification = Color(0xFF009EE7)
    val trackColor = Color.White.copy(alpha = 0.07f)
    val bannerThemed = Color(0xFF252526)
    val success = Color(0xFF1AC86A)
    val successText = Color(0xFF73EAA4)
    val surfaceVariant = Color.White.copy(alpha = 0.12f)
    val errorSurface = Color(0x4AE75454)
    val accessKey = GradientSpec(
        background = Color.Black,
        colors = listOf(
            Color(0xFF1E1E1E),
            Color(0xFF2A2A2A),
            Color(0xFF3C3C3C),
            Color(0xFF5A5A5A),
            Color(0xFF808080),
            Color(0xFF959595),
            Color(0xFF4A4A4A),
            Color(0xFF303030),
        ),
        stops = listOf(0f, 0.15f, 0.30f, 0.45f, 0.60f, 0.72f, 0.85f, 1f),
        borderColor = null,
        borderWidth = 0f,
    )
    val contactAvatar = GradientSpec(
        background = Color.Black,
        colors = listOf(
            Color(0xFF414141),
            Color(0xFF202020)
        ),
        stops = listOf(0f, 1f)
    )

    val chatColors = ChatColors(
        incomingBubble = Bubble(
            background = Color.White.copy(alpha = 0.02f),
            border = Color.White.copy(alpha = 0.03f),
        ),
        outgoingBubble = Bubble(
            background = Color.White.copy(alpha = 0.08f),
            border = Color.White.copy(alpha = 0.03f),
        ),
        typingIndicator = TypingIndicator(
            background = Color.White.copy(alpha = 0.02f),
            border = Color.White.copy(alpha = 0.03f),
            dots = Color.White.copy(alpha = 0.30f),
        )
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
        indicator = notification,
        action = Gray50,
        onAction = White,
        background = primary,
        onBackground = White,
        surface = primary,
        surfaceVariant = surfaceVariant,
        surfaceError = errorSurface,
        onSurface = White,
        error = Error,
        errorText = TextError,
        success = success,
        successText = success,
        textMain = Color.White,
        textSecondary = secondaryText,
        textTertiary = White10,
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
        accessKey = accessKey,
        contactAvatar = contactAvatar,
        chat = chatColors,
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
