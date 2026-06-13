package com.flipcash.app.core.chat

import android.os.Parcelable
import com.flipcash.services.models.chat.ChatId
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
    data class ByContact(val e164: String, val displayName: String, val chatId: ChatId? = null) : ChatIdentifier {
        override val key: String get() = e164
    }
}