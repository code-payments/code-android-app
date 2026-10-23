package com.getcode.util.resources

import android.content.Context
import android.provider.Settings

class AndroidSettingsHelper(
    private val context: Context
): SettingsHelper {
    override fun getGlobalIntSettingsValue(key: String): Int? {
        return Settings.Global.getInt(context.contentResolver, key)
    }

    override fun getGlobalFloatSettingsValue(key: String): Float? {
        return Settings.Global.getFloat(context.contentResolver, key)
    }
}