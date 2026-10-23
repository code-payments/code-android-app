package com.flipcash.app.core.chat

import android.os.Parcelable
import com.flipcash.app.core.contacts.DeviceContact
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.handle
import com.flipcash.services.models.nameOrHandle
import com.getcode.opencode.model.core.ID
import kotlinx.parcelize.Parcelize

/**
 * The counterparty a DM header and info card renders.
 *
 * A conversation is backed by one of two identity sources depending on its
 * [com.flipcash.services.models.chat.ChatType]:
 *
 * - [Contact] — a `CONTACT_DM`. Identity comes from a device [com.flipcash.app.core.contacts.DeviceContact]: it has a phone
 *   number and supports the "add to contacts" action.
 * - [TipUser] — a `TIP_DM`. The counterparty has no device contact; identity comes from their
 *   server [com.flipcash.services.models.UserProfile] (display name + profile picture), the same source the tips list uses.
 */
@Parcelize
sealed interface ChatParticipant: Parcelable {
    val displayName: String

    /**
     * The counterparty's public `@handle`, or null when there isn't one to show.
     *
     * Always null for a [Contact]: a `CONTACT_DM` is addressed by phone number, and the device
     * contact carries no Flipcash identity to read a username off. A [TipUser] has one whenever they
     * have claimed it.
     */
    val handle: String?

    /**
     * What to call this person — the one rule every surface that names them uses.
     *
     * [displayName] when they have one, [handle] when they don't. Null only when they have
     * neither, which for a [TipUser] means a profile the server sent us nothing identifying for,
     * and for a [Contact] means a device contact with an empty name.
     */
    val name: String? get() = nameOrHandle(displayName, handle)

    data class Contact(val contact: DeviceContact) : ChatParticipant {
        override val displayName: String get() = contact.displayName
        override val handle: String? get() = null
    }

    data class TipUser(val userId: ID, val profile: UserProfile) : ChatParticipant {
        override val displayName: String get() = profile.displayName
        override val handle: String? get() = profile.handle
    }
}