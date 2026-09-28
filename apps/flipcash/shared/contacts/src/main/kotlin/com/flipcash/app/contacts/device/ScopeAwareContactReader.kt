package com.flipcash.app.contacts.device

import com.flipcash.app.contacts.device.internal.FullAccessContactReader
import com.flipcash.app.contacts.device.internal.PickerContactReader
import com.flipcash.app.core.contacts.DeviceContact
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScopeAwareContactReader @Inject constructor(
    private val fullAccess: FullAccessContactReader,
    private val picker: PickerContactReader,
) : DeviceContactReader {

    override suspend fun readAll(): Result<Map<String, DeviceContact>> {
        val result = fullAccess.readAll()
        // If full-access failed (no permission) but the picker has contacts, use those.
        if (result.isFailure) {
            val pickerResult = picker.readAll()
            if (pickerResult.isSuccess && pickerResult.getOrThrow().isNotEmpty()) {
                return pickerResult
            }
        }
        return result
    }

    fun addSelectedContacts(contacts: List<PickedContactData>) {
        picker.addPickedContacts(contacts)
    }

    fun removeSelectedContact(e164: String) {
        picker.removePickedContact(e164)
    }

    fun reset() {
        picker.clearPickedContacts()
    }

    /** Returns true if READ_CONTACTS was previously used but is now denied. */
    suspend fun isPermissionRevoked(): Boolean = fullAccess.readAll().isFailure
}
