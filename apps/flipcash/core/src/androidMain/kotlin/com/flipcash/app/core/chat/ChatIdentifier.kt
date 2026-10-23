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
     * Retired: a tip DM addressed by the counterparty's Flipcash user id, which opened a gated chat
     * before it existed. Nothing builds one now; a person without a DM is reached through their
     * profile. It stays only so a route saved by an older build still decodes, and the app opens the
     * profile for it instead of a chat.
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
