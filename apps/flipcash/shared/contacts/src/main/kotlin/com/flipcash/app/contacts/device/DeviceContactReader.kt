package com.flipcash.app.contacts.device

import com.flipcash.app.core.contacts.DeviceContact

interface DeviceContactReader {
    suspend fun readAll(): Result<Map<String, DeviceContact>>
}

data class PickedContactData(
    val phoneNumber: String,
    val displayName: String,
    val photoUri: String? = null,
)
