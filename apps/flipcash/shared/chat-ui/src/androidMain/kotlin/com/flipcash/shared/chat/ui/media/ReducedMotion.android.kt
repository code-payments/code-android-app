package com.flipcash.shared.chat.ui.media

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Read straight from [Settings.Global] rather than through `rememberAnimationScale`, whose
 * `LocalSystemSettings` falls back to a stub where the host has not provided one. A missing or
 * unreadable setting means animations are on.
 */
@Composable
actual fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        runCatching {
            Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}
