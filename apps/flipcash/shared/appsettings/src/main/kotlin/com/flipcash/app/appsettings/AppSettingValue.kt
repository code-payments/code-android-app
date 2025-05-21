package com.flipcash.app.appsettings

sealed interface AppSettingValue {
    val key: String
    val default: Boolean get() = false

    companion object {
        val entries: List<AppSettingValue> = listOf(
            CameraStartByDefault,
            BiometricsRequired
        )
    }

    data object CameraStartByDefault: AppSettingValue {
        override val key: String = "camera_start_default"
        override val default: Boolean = true
    }

    data object BiometricsRequired: AppSettingValue {
        override val key: String = "require_biometrics"
    }
}