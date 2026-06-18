package com.flipcash.app.contacts.device.internal

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.flipcash.app.contacts.device.DeviceContactLookup
import com.flipcash.app.core.contacts.DeviceContact
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class AndroidDeviceContactLookup @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : DeviceContactLookup {

    override fun lookupDisplayName(e164: String): String? =
        lookupField(e164, ContactsContract.PhoneLookup.DISPLAY_NAME)

    override fun lookupPhotoUri(e164: String): String? =
        lookupField(e164, ContactsContract.PhoneLookup.PHOTO_URI)

    override fun lookupPhotoBytes(e164: String): ByteArray? {
        val contactId = lookupContactId(e164) ?: return null
        val contactUri = ContentUris.withAppendedId(
            ContactsContract.Contacts.CONTENT_URI, contactId
        )
        return try {
            ContactsContract.Contacts.openContactPhotoInputStream(
                context.contentResolver, contactUri, true
            )?.use { it.readBytes() }
        } catch (e: Exception) {
            null
        }
    }

    override fun lookupProfilePhoto(): ByteArray? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) return null

        return try {
            ContactsContract.Contacts.openContactPhotoInputStream(
                context.contentResolver, ContactsContract.Profile.CONTENT_URI, true
            )?.use { it.readBytes() }
        } catch (e: Exception) {
            null
        }
    }

    override fun lookupContact(e164: String): DeviceContact? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) return null

        val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI
            .buildUpon()
            .appendPath(e164)
            .build()

        return try {
            context.contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.PhoneLookup._ID,
                    ContactsContract.PhoneLookup.DISPLAY_NAME,
                    ContactsContract.PhoneLookup.PHOTO_URI,
                ),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    DeviceContact(
                        e164 = e164,
                        androidContactId = cursor.getLong(0),
                        displayName = cursor.getString(1) ?: e164,
                        photoUri = cursor.getString(2),
                    )
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun lookupContactId(e164: String): Long? =
        lookupField(e164, ContactsContract.PhoneLookup._ID)?.toLongOrNull()

    private fun lookupField(e164: String, column: String): String? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) return null

        val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI
            .buildUpon()
            .appendPath(e164)
            .build()

        return try {
            context.contentResolver.query(
                uri,
                arrayOf(column),
                null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val value = cursor.getString(0)?.takeIf { it.isNotEmpty() }
                    if (value != null) return@use value
                }
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}