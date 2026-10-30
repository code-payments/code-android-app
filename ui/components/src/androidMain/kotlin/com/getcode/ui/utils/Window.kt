package com.getcode.ui.utils

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext

enum class SystemNavigationMode {
    ThreeButton,
    TwoButton,
    Gesture
}
@Composable
fun rememberSystemNavigationMode(): State<SystemNavigationMode> {
    val context = LocalContext.current
    return produceState(getSystemNavigationMode(context)) {
        value = getSystemNavigationMode(context)
    }
}

private fun getSystemNavigationMode(context: Context): SystemNavigationMode {
    val modeInt = Settings.Secure.getInt(context.contentResolver, "navigation_mode", 0)

    return SystemNavigationMode.entries.getOrNull(modeInt) ?: SystemNavigationMode.ThreeButton
}