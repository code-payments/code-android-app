package com.getcode.util.resources

import androidx.compose.runtime.staticCompositionLocalOf

interface SettingsHelper {
    fun getGlobalIntSettingsValue(key: String): Int?
    fun getGlobalFloatSettingsValue(key: String): Float?
}

object StubSettingsHelper : SettingsHelper {
    override fun getGlobalIntSettingsValue(key: String): Int? = null
    override fun getGlobalFloatSettingsValue(key: String): Float? = null
}

val LocalSystemSettings = staticCompositionLocalOf<SettingsHelper> { StubSettingsHelper }