package com.flipcash.app.core.chat

import android.os.Parcelable
import com.flipcash.app.core.contacts.DeviceContact
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatId
import com.getcode.opencode.model.core.ID
import com.getcode.utils.hexEncodedString
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
sealed interface ChatIdentifier : Parcelable {
    val key: String

    @Serializable
    @Parcelize
    data class ByChatId(val chatId: ChatId) : ChatIdentifier {
        override val key: String get() = chatId.toString()
    }

    @Serializable
    @Parcelize
    data class ByContact(
        val contact: DeviceContact,
        val chatId: ChatId? = null
    ) : ChatIdentifier {
        override val key: String get() = contact.e164
    }

    /**
     * A tip DM addressed by the counterparty's Flipcash user id — the only identifier that can open
     * a conversation which does not exist yet.
     *
     * [ByChatId] and [ByContact] both name a chat the server already has: one by its id, one by a
     * phone number the server pre-derived an id for. Reaching someone by their `@handle` has
     * neither, so this carries the user id, which is what the canonical TIP_DM id is derived
     * from (`ChatCoordinator.generateChatId`) — deterministic and offline, so the chat opens on the
     * derived id and the first tip lands in it.
     *
     * [profile] rides along because the caller looked it up to get [userId] in the first place: the
     * header card renders from it on the first frame rather than waiting on a members fetch, which
     * for a chat with no messages would have nothing to return.
     */
    @Serializable
    @Parcelize
    data class ByUser(
        val userId: ID,
        val profile: UserProfile,
    ) : ChatIdentifier {
        override val key: String get() = userId.hexEncodedString()
    }
}
