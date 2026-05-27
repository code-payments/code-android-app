package com.flipcash.app.contacts.device.internal

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.flipcash.app.contacts.device.DeviceContact
import com.flipcash.app.contacts.device.DeviceContactReader
import com.flipcash.app.phone.PhoneUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class FullAccessContactReader @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val phoneUtils: PhoneUtils,
) : DeviceContactReader {

    override suspend fun readAll(): Result<Map<String, DeviceContact>> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return Result.failure(SecurityException("READ_CONTACTS not granted"))
        }

        val result = mutableMapOf<String, DeviceContact>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
        )

        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            null,
        )?.use { cursor ->
            val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val contactIdIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val photoIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)

            while (cursor.moveToNext()) {
                val rawNumber = cursor.getString(numberIdx) ?: continue
                val contactId = cursor.getLong(contactIdIdx)
                val displayName = cursor.getString(nameIdx) ?: continue
                val photoUri = cursor.getString(photoIdx)

                val e164 = normalizeToE164(rawNumber) ?: continue

                val existing = result[e164]
                if (existing == null || (existing.photoUri == null && photoUri != null)) {
                    result[e164] = DeviceContact(
                        e164 = e164,
                        androidContactId = contactId,
                        displayName = displayName,
                        photoUri = photoUri,
                    )
                }
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
