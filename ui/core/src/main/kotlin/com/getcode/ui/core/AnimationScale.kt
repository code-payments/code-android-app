package com.getcode.ui.core

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import com.getcode.util.resources.LocalSystemSettings
import com.getcode.util.resources.SettingsHelper

@Composable
fun rememberAnimationScale(): State<Float> {
    val settings = LocalSystemSettings.current

    return produceState(settings.animationScale()) {
        value = settings.animationScale()
    }
}

private fun SettingsHelper.animationScale(): Float {
    return runCatching {
        getGlobalFloatSettingsValue(Settings.Global.ANIMATOR_DURATION_SCALE)!!
    }.getOrDefault(1f)
}

fun Int.scaled(animationScale: Float): Long {
    return (this * animationScale).toLong()
}

fun Long.scaled(animationScale: Float): Long {
    return (this * animationScale).toLong()
}