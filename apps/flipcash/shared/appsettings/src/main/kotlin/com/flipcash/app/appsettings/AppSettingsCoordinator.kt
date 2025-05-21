package com.flipcash.app.appsettings

import androidx.compose.runtime.staticCompositionLocalOf
import com.flipcash.app.appsettings.internal.AppSettingMapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class AppSettingsCoordinator @Inject constructor(
    private val controller: AppSettingsController,
    private val mapper: AppSettingMapper,
) {
    fun settings(): Flow<List<AppSettingsItem>> = combine(
        AppSettingValue.entries.map { setting ->
            controller.observe(setting).map { value ->
                mapper.map(AppSetting(setting, value))
            }
        }
    ) { items -> items.toList() }

    fun observeValue(setting: AppSettingValue): Flow<Boolean> = controller.observe(setting)

    suspend fun get(setting: AppSettingValue): Boolean = controller.get(setting)
    fun update(setting: AppSettingValue, value: Boolean, fromUser: Boolean = true) = controller.update(setting, value, fromUser)
}

val LocalAppSettings = staticCompositionLocalOf<AppSettingsCoordinator> { error("No AppSettingsCoordinator provided") }