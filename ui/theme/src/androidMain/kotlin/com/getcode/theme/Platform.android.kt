package com.getcode.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.google.accompanist.systemuicontroller.rememberSystemUiController

@Composable
actual fun currentScreenSizeDp(): ScreenSizeDp {
    val configuration = LocalConfiguration.current
    return ScreenSizeDp(configuration.screenWidthDp, configuration.screenHeightDp)
}

@Composable
internal actual fun TransparentSystemBars() {
    val sysUiController = rememberSystemUiController()

    SideEffect {
        sysUiController.setStatusBarColor(color = Color(0x01000000))
        sysUiController.setNavigationBarColor(color = Color(0x01000000))
    }
}

actual fun Paint.makeBlur(shadowBlurRadius: Float) {
    this.asFrameworkPaint().apply {
        maskFilter =
            android.graphics.BlurMaskFilter(
                shadowBlurRadius, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
}

internal actual object PlatformFonts {
    actual val avenir = FontFamily(
        Font(R.font.avenir_next_regular, FontWeight.Thin),
        Font(R.font.avenir_next_regular, FontWeight.ExtraLight),
        Font(R.font.avenir_next_regular, FontWeight.Light),
        Font(R.font.avenir_next_regular, FontWeight.Normal),
        Font(R.font.avenir_next_demi, FontWeight.Medium), // TODO: find a better medium OTF
        Font(R.font.avenir_next_demi, FontWeight.SemiBold),
        Font(R.font.avenir_next_demi, FontWeight.Bold),
        Font(R.font.avenir_next_demi, FontWeight.ExtraBold),
        Font(R.font.avenir_next_demi, FontWeight.Black)
    )

    actual val avenirMedium = FontFamily(
        Font(R.font.avenir_next_medium, FontWeight.Normal),
        Font(R.font.avenir_next_medium, FontWeight.Medium),
        // Heavier overrides (e.g. caption.copy(fontWeight = Bold)) stay on Demi, as they did under Avenir.
        Font(R.font.avenir_next_demi, FontWeight.SemiBold),
        Font(R.font.avenir_next_demi, FontWeight.Bold),
        Font(R.font.avenir_next_demi, FontWeight.ExtraBold),
        Font(R.font.avenir_next_demi, FontWeight.Black),
    )

    actual val robotoMono = FontFamily(
        Font(R.font.roboto_mono_variable, FontWeight.Light),
        Font(R.font.roboto_mono_variable, FontWeight.Normal),
        Font(R.font.roboto_mono_variable, FontWeight.Medium),
        Font(R.font.roboto_mono_variable, FontWeight.SemiBold)
    )
}
