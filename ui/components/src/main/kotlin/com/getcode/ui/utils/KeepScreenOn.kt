package com.getcode.ui.utils

import android.app.Activity
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

/**
 * A composable function that keeps the screen on for a specified duration when enabled.
 * It can also optionally increase the screen brightness to a target level if it's below a minimum threshold.
 *
 * This is useful for scenarios like displaying a QR code where the user needs the screen to
 * stay on and be bright enough to be scanned.
 *
 * The effect is launched whenever the `isEnabled` parameter changes to `true`. After the specified
 * `timeoutMs`, the screen-on flag and any brightness changes are reverted.
 *
 * @param isEnabled A boolean flag to enable or disable the keep-screen-on functionality.
 * @param timeoutMs The duration in milliseconds for which to keep the screen on. Defaults to 10 seconds.
 * @param useBrightness A boolean flag to control whether the screen brightness should be adjusted.
 * @param minBrightness The minimum brightness threshold. If the current brightness is below this value
 *                      (or is set to automatic), the brightness will be adjusted. Brightness is a value from 0.0 to 1.0.
 * @param targetBrightness The brightness level to set the screen to if adjustment is needed. Brightness is a value from 0.0 to 1.0.
 */
@Composable
fun KeepScreenOn(
    isEnabled: Boolean,
    timeoutMs: Long = 10_000L,
    useBrightness: Boolean = false,
    minBrightness: Float = 0.4f,
    targetBrightness: Float = 0.6f
) {
    val context = LocalContext.current

    LaunchedEffect(isEnabled) {
        if (!isEnabled) return@LaunchedEffect

        val window = (context as Activity).window
        val layoutParams = window.attributes
        val originalBrightness = layoutParams.screenBrightness

        // When brightness is system-managed (adaptive brightness), the window
        // reports BRIGHTNESS_OVERRIDE_NONE (-1.0). We must query the actual
        // system brightness rather than treating -1.0 as "below threshold" —
        // otherwise we unconditionally override to targetBrightness which on
        // modern Pixel panels with non-linear curves appears as near-max.
        val effectiveBrightness = if (originalBrightness == WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) {
            Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                128 // ~50% fallback if unreadable
            ) / 255f
        } else {
            originalBrightness
        }

        if (useBrightness && effectiveBrightness < minBrightness) {
            layoutParams.screenBrightness = targetBrightness
            window.attributes = layoutParams
        }

        try {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            delay(timeoutMs)
        } finally {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (useBrightness) {
                layoutParams.screenBrightness = originalBrightness
                window.attributes = layoutParams
            }
        }
    }
}