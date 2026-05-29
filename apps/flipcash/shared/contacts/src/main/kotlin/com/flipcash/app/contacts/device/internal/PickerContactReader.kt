package com.flipcash.app.contacts.device.internal

import com.flipcash.app.contacts.device.DeviceContact
import com.flipcash.app.contacts.device.DeviceContactReader
import com.flipcash.app.contacts.device.PickedContactData
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

    private fun normalizeToE164(rawNumber: String): String? {
        val cleaned = rawNumber.filter { it.isDigit() || it == '+' }
        if (cleaned.isBlank()) return null

        val withPlus = if (cleaned.startsWith("+")) cleaned else "+$cleaned"
        if (!phoneUtils.isPhoneNumberValid(withPlus)) return null

        val countryCode = phoneUtils.getCountryCode(withPlus.removePrefix("+"))
        val locale = phoneUtils.countryLocales.find { it.countryCode == countryCode }
            ?: phoneUtils.defaultCountryLocale

        val result = phoneUtils.cleanNumber(withPlus.removePrefix("+${locale.phoneCode}"), locale)
        return result.ifBlank { null }
    }
}
