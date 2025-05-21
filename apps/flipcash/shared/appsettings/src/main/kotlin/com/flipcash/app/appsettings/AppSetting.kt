package com.flipcash.app.appsettings

data class AppSetting(
    val type: AppSettingValue,
    val enabled: Boolean,
)

data class AppSettingsItem(
    val setting: AppSetting,
    val name: Int,
    val description: Int? = null,
    val icon: Int,
    val visible: Boolean = true,
    val available: Boolean = true
)