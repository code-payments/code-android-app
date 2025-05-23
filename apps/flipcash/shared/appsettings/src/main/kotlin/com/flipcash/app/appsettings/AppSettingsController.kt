package com.flipcash.app.appsettings

import kotlinx.coroutines.flow.Flow

interface AppSettingsController {
    fun observe(): Flow<List<AppSetting>>
    fun observe(setting: AppSettingValue): Flow<Boolean>
    suspend fun get(setting: AppSettingValue): Boolean
    fun update(setting: AppSettingValue, value: Boolean, fromUser: Boolean = true)
    fun reset()
}

