package com.flipcash.services.models

import com.codeinc.flipcash.gen.push.v1.Model
import com.flipcash.services.internal.network.extensions.asPayload
import com.getcode.solana.keys.Mint
import com.getcode.utils.decodeBase64

enum class NotificationCategory {
    DEFAULT,
    DEPOSIT_WITHDRAWAL,
    BUY_SELL,
    GAIN,
    CHAT,
    CONTACT_JOIN,
}

sealed interface Substitution {
    data class Phone(
        val fallback: String,
        val phoneNumber: String,
    ): Substitution
}

data class NotificationPayload(
    val navigation: NavigationTrigger?,
    val category: NotificationCategory = NotificationCategory.DEFAULT,
    val groupKey: String = "",
    val titleSubstitutions: List<Substitution> = emptyList(),
    val bodySubstitutions: List<Substitution> = emptyList(),
) {
    companion object {
        fun fromEncoded(encoded: String): NotificationPayload? {
            val proto = runCatching { Model.Payload.parseFrom(encoded.decodeBase64()) }.getOrNull()
                ?: return null
            return fromProto(proto)
        }

        fun fromProto(proto: Model.Payload): NotificationPayload {
            return proto.asPayload()
        }
    }
}

sealed interface NavigationTrigger {
    data class CurrencyInfo(val mint: Mint) : NavigationTrigger
    data class Chat(val chatId: com.flipcash.services.models.chat.ChatId) : NavigationTrigger
}