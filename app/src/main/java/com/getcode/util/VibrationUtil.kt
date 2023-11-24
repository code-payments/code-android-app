package com.getcode.util

import android.content.Context.VIBRATOR_SERVICE
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import com.getcode.App


object VibrationUtil {
    fun vibrate(duration: Long = 100) {
        val v = App.getInstance().getSystemService(VIBRATOR_SERVICE) as Vibrator?

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v?.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            v?.vibrate(duration)
        }
    }
}