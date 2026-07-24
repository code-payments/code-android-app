package com.flipcash.app.messenger.internal

import com.flipcash.app.core.contacts.DeviceContact
import com.flipcash.services.models.UserProfile
import com.getcode.opencode.model.core.ID

/**
 * The counterparty a DM header and info card renders.
 *
 * A conversation is backed by one of two identity sources depending on its
 * [com.flipcash.services.models.chat.ChatType]:
 *
 * - [Contact] — a `CONTACT_DM`. Identity comes from a device [DeviceContact]: it has a phone
 *   number and supports the "add to contacts" action.
 * - [TipUser] — a `TIP_DM`. The counterparty has no device contact; identity comes from their
 *   server [UserProfile] (display name + profile picture), the same source the tips list uses.
 */
internal sealed interface ChatParticipant {
    val displayName: String

    data class Contact(val contact: DeviceContact) : ChatParticipant {
        override val displayName: String get() = contact.displayName
    }

    data class TipUser(val userId: ID, val profile: UserProfile) : ChatParticipant {
        override val displayName: String get() = profile.displayName.orEmpty()
    }
}
