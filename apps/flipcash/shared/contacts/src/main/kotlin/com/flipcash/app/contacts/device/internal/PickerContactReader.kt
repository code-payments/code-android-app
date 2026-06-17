package com.flipcash.app.contacts.device.internal

import com.flipcash.app.contacts.device.DeviceContactReader
import com.flipcash.app.contacts.device.PickedContactData
import com.flipcash.app.core.contacts.DeviceContact
import com.flipcash.app.phone.PhoneUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PickerContactReader @Inject constructor(
    private val phoneUtils: PhoneUtils,
) : DeviceContactReader {

    private val pickedContacts = MutableStateFlow<List<PickedContactData>>(emptyList())

    fun addPickedContacts(contacts: List<PickedContactData>) {
        pickedContacts.update { it + contacts }
    }

    fun removePickedContact(e164: String) {
        pickedContacts.update { list ->
            list.filterNot { normalizeToE164(it.phoneNumber) == e164 }
        }
    }

    fun clearPickedContacts() {
        pickedContacts.value = emptyList()
    }

    /**
     * Reads picked contacts and deduplicates by E.164-normalized phone number,
     * using the same first-occurrence-wins + photo-promotion strategy as
     * [FullAccessContactReader.readAll].
     */
    override suspend fun readAll(): Result<Map<String, DeviceContact>> {
        val raw = pickedContacts.value
        if (raw.isEmpty()) return Result.success(emptyMap())

        val result = mutableMapOf<String, DeviceContact>()
        for (contact in raw) {
            val e164 = normalizeToE164(contact.phoneNumber) ?: continue
            val existing = result[e164]
            if (existing == null || (existing.photoUri == null && contact.photoUri != null)) {
                result[e164] = DeviceContact(
                    e164 = e164,
                    androidContactId = 0L,
                    displayName = contact.displayName,
                    photoUri = contact.photoUri,
                )
            }
        }

        return Result.success(result)
    }

    private fun normalizeToE164(rawNumber: String): String? = phoneUtils.toE164(rawNumber)
}
