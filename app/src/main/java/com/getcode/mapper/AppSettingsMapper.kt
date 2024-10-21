package com.getcode.mapper

import androidx.biometric.BiometricManager
import com.getcode.R
import com.getcode.model.APP_SETTINGS
import com.getcode.model.PrefsBool
import com.getcode.models.SettingItem
import com.getcode.network.repository.AppSettings
import com.getcode.services.mapper.SuspendMapper
import javax.inject.Inject

class AppSettingsMapper @Inject constructor(
    private val biometricManager: BiometricManager,
): SuspendMapper<AppSettings, List<SettingItem>> {
    override suspend fun map(from: AppSettings): List<SettingItem> {

        return APP_SETTINGS.map { setting ->
            when (setting) {
                PrefsBool.CAMERA_START_BY_DEFAULT -> SettingItem(
                    type = setting,
                    name = R.string.title_autoStartCamera,
                    icon = R.drawable.ic_camera_outline,
                    enabled = from.cameraStartByDefault
                )

                PrefsBool.REQUIRE_BIOMETRICS -> {
                    val biometricsState = biometricManager.canAuthenticate(
                            BiometricManager.Authenticators.BIOMETRIC_WEAK
                    )

                    val canUseBiometrics = !(biometricsState == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ||
                            biometricsState == BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ||
                            biometricsState == BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ||
                            biometricsState == BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED)

                    val noBiometricsEnrolled = biometricsState == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
                    SettingItem(
                        type = setting,
                        name = R.string.title_requireBiometrics,
                        description = if (noBiometricsEnrolled) R.string.description_requireBiometricsNoneEnrolled else null,
                        icon = R.drawable.ic_biometrics,
                        enabled = from.requireBiometrics,
                        available = !noBiometricsEnrolled,
                        visible = canUseBiometrics
                    )
                }
            }
        }
    }

}