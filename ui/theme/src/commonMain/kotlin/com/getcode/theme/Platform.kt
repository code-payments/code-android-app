package com.getcode.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily

/** The current window's size in dp, as Android's `Configuration.screenWidthDp`/`screenHeightDp` report it. */
data class ScreenSizeDp(val widthDp: Int, val heightDp: Int)

@Composable
expect fun currentScreenSizeDp(): ScreenSizeDp

/** Makes the status and navigation bars transparent where the platform has them. */
@Composable
internal expect fun TransparentSystemBars()

internal expect object PlatformFonts {
    val avenir: FontFamily
    val avenirMedium: FontFamily
    val robotoMono: FontFamily
}
