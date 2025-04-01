package com.getcode.util.vibration

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.VibratorManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object VibratorModule {

    @SuppressLint("NewApi")
    @Provides
    @Singleton
    fun providesVibrator(
        @ApplicationContext context: Context
    ): Vibrator = when (val apiLevel = Build.VERSION.SDK_INT) {
        in Build.VERSION_CODES.BASE..Build.VERSION_CODES.R -> {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            if (apiLevel >= Build.VERSION_CODES.O) {
                Api26Vibrator(vibrator)
            } else {
                Api25Vibrator(vibrator)
            }
        }

        else -> Api31Vibrator(context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
    }
}