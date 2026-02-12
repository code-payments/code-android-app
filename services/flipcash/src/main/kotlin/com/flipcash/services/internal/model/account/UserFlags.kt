package com.flipcash.services.internal.model.account

import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import kotlin.time.Duration

data class UserFlags(
    val isStaff: Boolean,
    val isRegistered: Boolean,
    val requiresIapForRegistration: Boolean,
    val preferredOnRampProvider: OnRampProvider?,
    val supportedOnRampProviders: List<OnRampProvider>,
    val minimumVersion: Int?,
    val billExchangeDataTimeout: Duration?,
)
