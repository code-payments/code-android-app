package com.flipcash.app.contacts

import com.flipcash.app.contacts.device.DeviceContactLookup
import com.flipcash.app.persistence.sources.ContactDataSource
import com.flipcash.app.phone.PhoneUtils
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactResolver @Inject constructor(
    private val contactDataSource: ContactDataSource,
    private val deviceContactLookup: DeviceContactLookup,
    private val phoneUtils: PhoneUtils,
) {
    suspend fun resolveName(e164: String, fallback: String = e164): String {
        val cached = contactDataSource.getDisplayName(e164)
        val device = deviceContactLookup.lookupDisplayName(e164)

        if (device != null) {
            if (device != cached) contactDataSource.updateDisplayName(e164, device)
            return device
        }

        return cached
            ?: runCatching { phoneUtils.formatNumber(e164) }.getOrDefault(fallback)
    }

    suspend fun resolvePhotoUri(e164: String): String? {
        val cached = contactDataSource.getPhotoUri(e164)
        if (cached != null) return cached

        val device = deviceContactLookup.lookupPhotoUri(e164) ?: return null
        contactDataSource.updatePhotoUri(e164, device)
        return device
    }

    fun resolvePhotoBytes(e164: String): ByteArray? =
        deviceContactLookup.lookupPhotoBytes(e164)

    fun resolveOwnPhotoBytes(ownE164: String?): ByteArray? =
        deviceContactLookup.lookupProfilePhoto()
            ?: ownE164?.let { deviceContactLookup.lookupPhotoBytes(it) }
}