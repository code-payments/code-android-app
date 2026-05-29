package com.flipcash.app.directsend.internal

import com.flipcash.app.contacts.device.DeviceContact

internal sealed interface ContactListItem {
    data class Header(val title: String) : ContactListItem
    data class ContactRow(val contact: DeviceContact, val isOnFlipcash: Boolean) : ContactListItem
}
