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
    suspend fun resolveName(e164: String, fallback: String = e164): String =
        contactDataSource.getDisplayName(e164)
            ?: deviceContactLookup.lookupDisplayName(e164)
            ?: runCatching { phoneUtils.formatNumber(e164) }.getOrDefault(fallback)

    suspend fun resolvePhotoUri(e164: String): String? =
        contactDataSource.getPhotoUri(e164)
}