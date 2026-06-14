package com.flipcash.app.contacts.device

import com.flipcash.app.phone.PhoneUtils

interface DeviceContactReader {
    suspend fun readAll(): Result<Map<String, DeviceContact>>
}

data class DeviceContact(
    val e164: String,
    val androidContactId: Long,
    val displayName: String,
    val photoUri: String?,
    val displayNumber: String = "",
) {
    val isUnknown = androidContactId == -1L
    companion object {
        fun unknownContact(
            e164: String = "",
            displayName: String? = null,
            displayNumber: String? = null,
        ) = DeviceContact(
            e164 = e164,
            androidContactId = -1,
            displayName = displayName ?: e164,
            displayNumber = displayNumber ?: e164,
            photoUri = null,
        )
    }
}

data class PickedContactData(
    val phoneNumber: String,
    val displayName: String,
    val photoUri: String? = null,
)
