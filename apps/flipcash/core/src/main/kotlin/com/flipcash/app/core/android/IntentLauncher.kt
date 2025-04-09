package com.flipcash.app.core.android

import android.content.Context
import com.flipcash.app.core.android.extensions.launchAppSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class IntentLauncher @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun launchAppSettings() {
        context.launchAppSettings()
    }
}