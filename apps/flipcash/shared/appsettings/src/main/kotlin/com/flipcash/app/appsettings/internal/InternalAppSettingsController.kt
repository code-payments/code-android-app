package com.flipcash.app.appsettings.internal

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.flipcash.app.appsettings.AppSetting
import com.flipcash.app.appsettings.AppSettingValue
import com.flipcash.app.appsettings.AppSettingsController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class InternalAppSettingsController(
    private val context: Context,
) : AppSettingsController {

    companion object {
        private val AppSettingValue.booleanPreferenceKey
            get() = booleanPreferencesKey(key)
    }

    private val dataScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val appSettings = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler(
            produceNewData = { emptyPreferences() }
        ),
        migrations = listOf(),
        scope = dataScope,
        produceFile = { context.preferencesDataStoreFile("app-settings") }
    )

    override fun observe(): Flow<List<AppSetting>> = appSettings.data.map { prefs ->
        AppSettingValue.entries.map {
            val value = prefs[it.booleanPreferenceKey] ?: it.default

            AppSetting(it, value)
        }
    }.stateIn(
        dataScope,
        started = SharingStarted.Eagerly,
        AppSettingValue.entries.map { AppSetting(it, it.default) }
    )

    override fun observe(setting: AppSettingValue): Flow<Boolean> = appSettings.data.map { prefs ->
        prefs[setting.booleanPreferenceKey] ?: false
    }.stateIn(dataScope, started = SharingStarted.Eagerly, setting.default)

    override suspend fun get(setting: AppSettingValue): Boolean {
        return appSettings.data.map { prefs ->
            prefs[setting.booleanPreferenceKey]
        }.firstOrNull() ?: false
    }

    override fun update(setting: AppSettingValue, value: Boolean, fromUser: Boolean) {
        if (fromUser) {
            // TODO: analytics
        }

        dataScope.launch(Dispatchers.IO) {
            appSettings.edit { prefs ->
                prefs[setting.booleanPreferenceKey] = value
            }
        }
    }
}