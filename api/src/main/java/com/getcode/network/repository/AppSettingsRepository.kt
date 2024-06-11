package com.getcode.network.repository

import com.getcode.analytics.AnalyticsService
import com.getcode.model.AppSetting
import com.getcode.model.PrefsBool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

data class AppSettings(
    val cameraStartByDefault: Boolean
) {
    companion object {
        val Defaults = AppSettings(
            cameraStartByDefault = true
        )
    }
}
class AppSettingsRepository @Inject constructor(
    private val prefRepository: PrefRepository,
    private val analytics: AnalyticsService,
) {

    fun observe(): Flow<AppSettings> = AppSettings.Defaults.let { defaults ->
        prefRepository.observeOrDefault(PrefsBool.CAMERA_START_BY_DEFAULT, defaults.cameraStartByDefault)
            .map { AppSettings(it) }
    }

    suspend fun get(setting: AppSetting): Boolean {
        return when (setting) {
            PrefsBool.CAMERA_START_BY_DEFAULT -> prefRepository.get(
                PrefsBool.CAMERA_START_BY_DEFAULT,
                AppSettings.Defaults.cameraStartByDefault
            )
        }
    }

    fun update(setting: AppSetting, value: Boolean) {
        analytics.appSettingToggled(setting, value)
        when (setting) {
            PrefsBool.CAMERA_START_BY_DEFAULT -> {
                prefRepository.set(PrefsBool.CAMERA_START_BY_DEFAULT, value)
            }
        }
    }
}