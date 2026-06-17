package com.flipcash.app.core.contacts

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
data class DeviceContact(
    val e164: String,
    val androidContactId: Long = -1L,
    val displayName: String,
    val photoUri: String?,
    val displayNumber: String = "",
): Parcelable {
    var isUnknown: Boolean = false
        private set

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
        ).apply { isUnknown = true }
    }
}