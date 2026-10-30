package com.getcode.theme

import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * The user's Material You (Monet) accent color, derived from their wallpaper / system theme.
 *
 * On Android 12+ (API 31) this reads the wallpaper-derived tonal palette via Material3's
 * [dynamicDarkColorScheme] and returns its [accent] (`primary`) and [onAccent] (`onPrimary`).
 * The app is dark-only, so we always take the *dark* dynamic scheme regardless of the system
 * light/dark setting — its tonal steps read correctly on our dark surfaces.
 *
 * On API < 31 both fields fall back to [fallbackAccent] / [fallbackOnAccent].
 *
 * Usage:
 * ```
 * val (accent, onAccent) = rememberDynamicAccent(
 *     fallbackAccent = CodeTheme.colors.secondary,
 *     fallbackOnAccent = CodeTheme.colors.onAction,
 * )
 * Button(colors = ButtonDefaults.buttonColors(containerColor = accent)) {
 *     Text("Save", color = onAccent)
 * }
 * ```
 */
@Composable
fun rememberDynamicAccent(
    fallbackAccent: Color,
    fallbackOnAccent: Color = Color.White,
): DynamicAccent {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return DynamicAccent(accent = fallbackAccent, onAccent = fallbackOnAccent)
    }
    val context = LocalContext.current
    return remember(context) {
        val scheme = dynamicDarkColorScheme(context)
        DynamicAccent(accent = scheme.primary, onAccent = scheme.onPrimary)
    }
}

/** A wallpaper-derived accent paired with the content color that reads on top of it. */
data class DynamicAccent(
    val accent: Color,
    val onAccent: Color,
)
