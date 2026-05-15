package com.getcode.ui.utils

import android.app.Activity
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.math.min

/**
 * A composable function that keeps the screen on while enabled and optionally adjusts brightness.
 *
 * This is useful for scenarios like displaying a QR code where the user needs the screen to
 * stay on and be bright enough to be scanned.
 *
 * The keep-screen-on flag and brightness adjustment remain active for the entire duration that
 * [isEnabled] is `true` and the app is in the foreground. When [isEnabled] flips to `false`,
 * the composable leaves composition, or the app is backgrounded, the flag is cleared and
 * brightness is restored. Returning to the foreground re-applies the overrides.
 *
 * When [useBrightness] is enabled and the screen is very dim (below [minBrightness]), brightness
 * is boosted by [brightnessBoost] relative to the current level (capped at [maxBrightness]).
 * This avoids jarring jumps on OLED panels with non-linear brightness curves.
 *
 * @param isEnabled A boolean flag to enable or disable the keep-screen-on functionality.
 * @param useBrightness A boolean flag to control whether the screen brightness should be adjusted.
 * @param minBrightness The minimum brightness threshold below which a boost is applied. 0.0 to 1.0.
 * @param brightnessBoost The amount to add to current brightness when boosting. 0.0 to 1.0.
 * @param maxBrightness The maximum brightness cap when boosting. 0.0 to 1.0.
 */
@Composable
fun KeepScreenOn(
    isEnabled: Boolean,
    useBrightness: Boolean = false,
    minBrightness: Float = 0.1f,
    brightnessBoost: Float = 0.3f,
    maxBrightness: Float = 0.8f
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val currentIsEnabled by rememberUpdatedState(isEnabled)
    var originalBrightness by remember { mutableFloatStateOf(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) }
    var brightnessAdjusted by remember { mutableStateOf(false) }

    DisposableEffect(isEnabled, lifecycleOwner) {
        if (!isEnabled) return@DisposableEffect onDispose { }

        val window = (context as Activity).window

        fun clearOverrides() {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (brightnessAdjusted) {
                val layoutParams = window.attributes
                layoutParams.screenBrightness = originalBrightness
                window.attributes = layoutParams
                brightnessAdjusted = false
            }
        }

        fun applyOverrides() {
            if (!currentIsEnabled) {
                clearOverrides()
                return
            }

            val layoutParams = window.attributes

            // Only capture original brightness on the first apply — not on
            // re-apply from ON_RESUME — to avoid saving the boosted value.
            if (!brightnessAdjusted) {
                originalBrightness = layoutParams.screenBrightness
            }

            val effectiveBrightness = if (originalBrightness == WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) {
                Settings.System.getInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    128
                ) / 255f
            } else {
                originalBrightness
            }

            brightnessAdjusted = useBrightness && effectiveBrightness < minBrightness
            if (brightnessAdjusted) {
                layoutParams.screenBrightness = min(effectiveBrightness + brightnessBoost, maxBrightness)
                window.attributes = layoutParams
            }

            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        applyOverrides()

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> clearOverrides()
                Lifecycle.Event.ON_RESUME -> applyOverrides()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            clearOverrides()
        }
    }
}
