package com.flipcash.app.contacts.device

interface DeviceContactReader {
    suspend fun readAll(): Result<Map<String, DeviceContact>>
}

data class DeviceContact(
    val e164: String,
    val androidContactId: Long,
    val displayName: String,
    val photoUri: String?,
)
