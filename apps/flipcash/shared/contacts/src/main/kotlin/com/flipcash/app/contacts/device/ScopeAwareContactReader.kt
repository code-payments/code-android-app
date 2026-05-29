package com.flipcash.app.contacts.device

import com.flipcash.app.contacts.device.internal.FullAccessContactReader
import com.flipcash.app.contacts.device.internal.PickerContactReader
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.FeatureFlagController
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScopeAwareContactReader @Inject constructor(
    private val fullAccess: FullAccessContactReader,
    private val picker: PickerContactReader,
    private val featureFlags: FeatureFlagController,
) : DeviceContactReader {

    override suspend fun readAll(): Result<Map<String, DeviceContact>> {
        val reader = activeReader()
        val result = reader.readAll()
        // If full-access failed (no permission) but the picker has contacts, use those.
        if (result.isFailure && reader === fullAccess) {
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

    /**
     * Returns true if READ_CONTACTS was previously used but is now denied.
     * Always false in picker mode — picker never holds READ_CONTACTS.
     */
    suspend fun isPermissionRevoked(): Boolean {
        if (featureFlags.observe(FeatureFlag.ContactPickerMode).value) return false
        return fullAccess.readAll().isFailure
    }

    private fun activeReader(): DeviceContactReader =
        if (featureFlags.observe(FeatureFlag.ContactPickerMode).value) picker else fullAccess
}
