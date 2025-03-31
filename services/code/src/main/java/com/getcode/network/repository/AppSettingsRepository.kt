package com.getcode.network.repository

import com.getcode.analytics.CodeAnalyticsService
import com.getcode.services.model.AppSetting
import com.getcode.services.model.PrefsBool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

data class AppSettings(
    val cameraStartByDefault: Boolean,
    val requireBiometrics: Boolean,
) {
    companion object {
        val Defaults = AppSettings(
            cameraStartByDefault = true,
            requireBiometrics = false,
        )
    }
}
class AppSettingsRepository @Inject constructor(
    private val prefRepository: PrefRepository,
    private val analytics: CodeAnalyticsService,
) {

    fun observe(): Flow<AppSettings> = AppSettings.Defaults.let { defaults ->
        combine(
            prefRepository.observeOrDefault(PrefsBool.CAMERA_START_BY_DEFAULT, defaults.cameraStartByDefault),
            prefRepository.observeOrDefault(PrefsBool.REQUIRE_BIOMETRICS, defaults.requireBiometrics)
        ) { camera, biometrics ->
            AppSettings(
                cameraStartByDefault = camera,
                requireBiometrics = biometrics
            )
        }
    }

    suspend fun get(setting: AppSetting): Boolean {
        return when (setting) {
            PrefsBool.CAMERA_START_BY_DEFAULT -> prefRepository.get(
                PrefsBool.CAMERA_START_BY_DEFAULT,
                AppSettings.Defaults.cameraStartByDefault
            )

            PrefsBool.REQUIRE_BIOMETRICS -> prefRepository.get(
                PrefsBool.REQUIRE_BIOMETRICS,
                AppSettings.Defaults.requireBiometrics
            )
        }
    }

    fun update(setting: AppSetting, value: Boolean, fromUser: Boolean = true) {
        if (fromUser) {
            analytics.appSettingToggled(setting, value)
        }
        when (setting) {
            PrefsBool.CAMERA_START_BY_DEFAULT -> {
                prefRepository.set(PrefsBool.CAMERA_START_BY_DEFAULT, value)
            }

            PrefsBool.REQUIRE_BIOMETRICS -> {
                prefRepository.set(PrefsBool.REQUIRE_BIOMETRICS, value)
            }
        }
    }
}