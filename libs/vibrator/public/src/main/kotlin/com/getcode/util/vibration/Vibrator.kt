package com.getcode.util.vibration

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

interface Vibrator {
    fun vibrate(duration: Long = 100)
    fun tick()
}

private class NoVibration : Vibrator {
    override fun vibrate(duration: Long) {

    }

    override fun tick() {

    }
}

val LocalVibrator: ProvidableCompositionLocal<Vibrator> = staticCompositionLocalOf { NoVibration() }