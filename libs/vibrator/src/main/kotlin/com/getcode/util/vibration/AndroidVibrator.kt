package com.getcode.util.vibration

import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import androidx.annotation.RequiresApi

class Api25Vibrator(
    private val vibrator: android.os.Vibrator
) : Vibrator {
    override fun vibrate(duration: Long) {
        vibrator.vibrate(duration)
    }

    override fun tick() {
        vibrator.vibrate(50)
    }
}

@RequiresApi(Build.VERSION_CODES.O)
class Api26Vibrator(
    private val vibrator: android.os.Vibrator
) : Vibrator {
    override fun vibrate(duration: Long) {
        vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    override fun tick() {
        vibrator.vibrate(VibrationEffect.createOneShot(50,  HapticFeedbackConstants.CLOCK_TICK))
    }
}

@RequiresApi(Build.VERSION_CODES.S)
class Api31Vibrator(
    private val vibrator: VibratorManager
) : Vibrator {

    override fun vibrate(duration: Long) {
        vibrator.vibrate(
            CombinedVibration.createParallel(
                VibrationEffect.createOneShot(
                    50,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
        )
    }

    override fun tick() {
        vibrator.vibrate(
            CombinedVibration.createParallel(
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            )
        )
    }
}