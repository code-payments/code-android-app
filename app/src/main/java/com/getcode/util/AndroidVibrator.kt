package com.getcode.util

import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.annotation.RequiresApi
import com.getcode.util.vibration.Vibrator

class Api25Vibrator(
    private val vibrator: android.os.Vibrator
) : Vibrator {
    override fun vibrate(duration: Long) {
        vibrator.vibrate(duration)
    }
}

@RequiresApi(Build.VERSION_CODES.O)
class Api26Vibrator(
    private val vibrator: android.os.Vibrator
) : Vibrator {
    override fun vibrate(duration: Long) {
        vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
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
                    duration,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
        )
    }
}