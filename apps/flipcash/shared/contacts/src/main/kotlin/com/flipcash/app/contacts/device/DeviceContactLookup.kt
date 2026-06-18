package com.flipcash.app.contacts.device

import com.flipcash.app.core.contacts.DeviceContact

interface DeviceContactLookup {
    fun lookupDisplayName(e164: String): String?
    fun lookupPhotoUri(e164: String): String?
    fun lookupPhotoBytes(e164: String): ByteArray?
    fun lookupProfilePhoto(): ByteArray?
    fun lookupContact(e164: String): DeviceContact?
}