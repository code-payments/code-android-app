package com.flipcash.services.internal.model.account

import com.flipcash.services.internal.model.thirdparty.OnRampProvider

data class UserFlags(
    val isStaff: Boolean,
    val isRegistered: Boolean,
    val requiresIapForRegistration: Boolean,
    val preferredOnRampProvider: OnRampProvider?,
    val supportedOnRampProviders: List<OnRampProvider>,
)
