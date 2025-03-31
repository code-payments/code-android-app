package com.getcode.models

import com.getcode.services.model.AppSetting

data class SettingItem(
    val type: AppSetting,
    val name: Int,
    val description: Int? = null,
    val icon: Int,
    val enabled: Boolean,
    val visible: Boolean = true,
    val available: Boolean = true
)