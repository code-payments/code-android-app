package com.getcode.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject


class Pacman @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val packageManager = context.packageManager

    fun enableTweetShare(enable: Boolean) {
        val component = ComponentName(context.packageName, "com.getcode.view.TweetShareHandler")
        if (enable) {
            packageManager.setComponentEnabledSetting(
                component,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP
            )
        } else {
            packageManager.setComponentEnabledSetting(
                component,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP
            )
        }
    }

}