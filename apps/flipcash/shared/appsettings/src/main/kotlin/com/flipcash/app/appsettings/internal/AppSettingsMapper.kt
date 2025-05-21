package com.flipcash.app.appsettings.internal

import androidx.biometric.BiometricManager
import com.flipcash.app.appsettings.AppSetting
import com.flipcash.app.appsettings.AppSettingValue
import com.flipcash.app.appsettings.AppSettingsItem
import com.flipcash.shared.appsettings.R
import com.getcode.libs.biometrics.Biometrics
import com.getcode.opencode.mapper.SuspendMapper
import javax.inject.Inject

class AppSettingMapper @Inject constructor(
    private val biometricManager: BiometricManager,
) : SuspendMapper<AppSetting, AppSettingsItem> {
    override suspend fun map(from: AppSetting): AppSettingsItem {
        return when (from.type) {
            AppSettingValue.BiometricsRequired -> {
                val biometricsState = biometricManager.canAuthenticate(Biometrics.TEST_AUTH)
                val canUseBiometrics =
                    !(biometricsState == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ||
                            biometricsState == BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ||
                            biometricsState == BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ||
                            biometricsState == BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED)

                val noBiometricsEnrolled =
                    biometricsState == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED

                AppSettingsItem(
                    setting = from,
                    name = R.string.title_requireBiometrics,
                    description = if (noBiometricsEnrolled) R.string.description_requireBiometricsNoneEnrolled else null,
                    icon = R.drawable.ic_biometrics,
                    available = !noBiometricsEnrolled,
                    visible = canUseBiometrics

                )
            }
            AppSettingValue.CameraStartByDefault -> {
                AppSettingsItem(
                    setting = from,
                    name = R.string.title_autoStartCamera,
                    icon = R.drawable.ic_camera_outline,
                )
            }
        }
    }
}