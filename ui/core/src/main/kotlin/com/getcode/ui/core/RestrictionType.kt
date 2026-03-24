package com.getcode.ui.core

import kotlinx.serialization.Serializable

@Serializable
enum class RestrictionType {
    ACCESS_EXPIRED,
    FORCE_UPGRADE,
    TIMELOCK_UNLOCKED
}