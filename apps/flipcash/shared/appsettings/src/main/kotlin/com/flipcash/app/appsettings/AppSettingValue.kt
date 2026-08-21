package com.flipcash.app.appsettings

sealed interface AppSettingValue {
    val key: String
    val default: Boolean get() = false

    companion object {
        val entries: List<AppSettingValue> by lazy {
            listOf(
                BiometricsRequired
            )
        }
    }

    data object BiometricsRequired: AppSettingValue {
        override val key: String = "require_biometrics"
    }
}