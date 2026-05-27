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

    override suspend fun readAll(): Result<Map<String, DeviceContact>> = activeReader().readAll()

    fun addSelectedContacts(contactIds: List<Long>) {
        picker.addPickedContacts(contactIds)
    }

    fun reset() {
        picker.clearPickedContacts()
    }

    private fun activeReader(): DeviceContactReader =
        if (featureFlags.observe(FeatureFlag.ContactPickerMode).value) picker else fullAccess
}
