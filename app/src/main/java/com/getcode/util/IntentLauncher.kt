package com.getcode.util

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class IntentLauncher @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun launchAppSettings() {
        context.launchAppSettings()
    }

    fun launchSmsIntent(phoneValue: String, message: String) {
        context.launchSmsIntent(phoneValue, message)
    }
}