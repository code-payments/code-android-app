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

    /**
     * Reads all device contacts and deduplicates by E.164-normalized phone number.
     *
     * The returned map is keyed by E.164, so each normalized number appears exactly once.
     * When multiple rows resolve to the same E.164 (e.g. "+1 555-1234" and "5551234"),
     * the **first occurrence wins** for `displayName` and `androidContactId`, with one
     * exception: if the existing entry has no photo and a later row does, the later row's
     * full record replaces the earlier one (photo promotion).
     *
     * A single Android contact with multiple phone numbers produces **separate** map entries,
     * one per distinct E.164.
     *
     * | Scenario                              | Result                                          |
     * |---------------------------------------|-------------------------------------------------|
     * | First occurrence of an E.164          | Inserted as-is                                  |
     * | Duplicate E.164, existing has photo   | Skipped (first-occurrence-wins)                 |
     * | Duplicate E.164, existing lacks photo | Replaced (photo promotion)                      |
     * | Raw number fails E.164 normalization  | Skipped entirely                                |
     * | Multi-number contact                  | Each valid E.164 becomes its own entry          |
     */
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

    private fun normalizeToE164(rawNumber: String): String? = phoneUtils.toE164(rawNumber)
}
