package com.flipcash.services.internal.model.account

data class UserFlags(
    val isStaff: Boolean,
    val isRegistered: Boolean,
    val requiresIapForRegistration: Boolean
)
