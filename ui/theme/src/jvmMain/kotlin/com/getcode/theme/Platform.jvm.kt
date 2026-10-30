package com.getcode.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.skiaPaint
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.MaskFilter

@Composable
actual fun currentScreenSizeDp(): ScreenSizeDp {
    val size = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current.density
    return ScreenSizeDp((size.width / density).toInt(), (size.height / density).toInt())
}

@Composable
internal actual fun TransparentSystemBars() = Unit

actual fun Paint.makeBlur(shadowBlurRadius: Float) {
    // Skia's own radius-to-sigma conversion, which Android's BlurMaskFilter applies internally.
    val sigma = if (shadowBlurRadius > 0f) 0.57735f * shadowBlurRadius + 0.5f else 0f
    skiaPaint.maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, sigma)
}

// The same font files Android packages from res/font, put on the classpath by this module's build.
private fun font(name: String, weight: FontWeight) = Font(
    identity = "$name-${weight.weight}",
    data = PlatformFonts::class.java.getResourceAsStream("/font/$name")!!.use { it.readBytes() },
    weight = weight,
)

internal actual object PlatformFonts {
    actual val avenir = FontFamily(
        font("avenir_next_regular.otf", FontWeight.Thin),
        font("avenir_next_regular.otf", FontWeight.ExtraLight),
        font("avenir_next_regular.otf", FontWeight.Light),
        font("avenir_next_regular.otf", FontWeight.Normal),
        font("avenir_next_demi.otf", FontWeight.Medium),
        font("avenir_next_demi.otf", FontWeight.SemiBold),
        font("avenir_next_demi.otf", FontWeight.Bold),
        font("avenir_next_demi.otf", FontWeight.ExtraBold),
        font("avenir_next_demi.otf", FontWeight.Black),
    )

    actual val avenirMedium = FontFamily(
        font("avenir_next_medium.otf", FontWeight.Normal),
        font("avenir_next_medium.otf", FontWeight.Medium),
        font("avenir_next_demi.otf", FontWeight.SemiBold),
        font("avenir_next_demi.otf", FontWeight.Bold),
        font("avenir_next_demi.otf", FontWeight.ExtraBold),
        font("avenir_next_demi.otf", FontWeight.Black),
    )

    actual val robotoMono = FontFamily(
        font("roboto_mono_variable.ttf", FontWeight.Light),
        font("roboto_mono_variable.ttf", FontWeight.Normal),
        font("roboto_mono_variable.ttf", FontWeight.Medium),
        font("roboto_mono_variable.ttf", FontWeight.SemiBold),
    )
}
