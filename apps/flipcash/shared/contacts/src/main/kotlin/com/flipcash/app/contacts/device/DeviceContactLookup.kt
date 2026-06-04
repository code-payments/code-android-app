package com.flipcash.app.contacts.device

interface DeviceContactLookup {
    fun lookupDisplayName(e164: String): String?
    fun lookupPhotoUri(e164: String): String?
}