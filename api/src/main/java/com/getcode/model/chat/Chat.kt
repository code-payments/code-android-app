package com.getcode.model.chat

import com.codeinc.gen.chat.v2.ChatService
import com.codeinc.gen.chat.v2.ChatService.ExchangeDataContent
import com.codeinc.gen.chat.v2.ChatService.ExchangeDataContent.ReferenceCase
import com.getcode.model.Cursor
import com.getcode.model.ID
import com.getcode.solana.keys.Signature as SolanaKeysSignature

/**
 * Chat domain model for On-Chain messaging. This serves as a reference to a collection of messages.
 *
 * @param id Unique chat identifier ([ID])
 * @param type The type of chat
 * @param title The chat title, which will be localized by server when applicable
 * @param members The members in this chat
 * For [ChatType.Notification], this list has exactly 1 item
 * For [ChatType.TwoWay], this list has exactly 2 items
 * @param canMute Can the user mute this chat?
 * @param canUnsubscribe Can the user unsubscribe from this chat?
 * @param cursor [Cursor] value for this chat for reference in subsequent GetChatsRequest
 * @param messages List of messages within this chat
 */
data class Chat(
    val id: ID,
    val type: ChatType,
    val title: String,
    val members: List<ChatMember>,
    val canMute: Boolean,
    val canUnsubscribe: Boolean,
    val cursor: Cursor,
    val messages: List<ChatMessage>
) {
    val unreadCount: Int
        get() {
            val self = members.firstOrNull { it.isSelf } ?: return 0
            return self.numUnread
        }

    fun resetUnreadCount(): Chat {
        val self = members.firstOrNull { it.isSelf } ?: return this
        val updatedSelf = self.copy(numUnread = 0)
        val updatedMembers = members.map {
            if (it.id == self.id) {
                updatedSelf
            } else {
                it
            }
        }
        return copy(members = updatedMembers)
    }

    val isMuted: Boolean
        get() {
            val self = members.firstOrNull { it.isSelf } ?: return false
            return self.isMuted
        }

    fun setMuteState(muted: Boolean): Chat {
        val self = members.firstOrNull { it.isSelf } ?: return this
        val updatedSelf = self.copy(isMuted = muted)
        val updatedMembers = members.map {
            if (it.id == self.id) {
                updatedSelf
            } else {
                it
            }
        }
        return copy(members = updatedMembers)
    }

    val isSubscribed: Boolean
        get() {
            val self = members.firstOrNull { it.isSelf } ?: return false
            return self.isSubscribed
        }

    fun setSubscriptionState(subscribed: Boolean): Chat {
        val self = members.firstOrNull { it.isSelf } ?: return this
        val updatedSelf = self.copy(isSubscribed = subscribed)
        val updatedMembers = members.map {
            if (it.id == self.id) {
                updatedSelf
            } else {
                it
            }
        }
        return copy(members = updatedMembers)
    }

    val newestMessage: ChatMessage?
        get() = messages.maxByOrNull { it.dateMillis }

    val lastMessageMillis: Long?
        get() = newestMessage?.dateMillis
}




enum class ChatType {
    Unknown,
    Notification,
    TwoWay;

    companion object {
        operator fun invoke(proto: ChatService.ChatType): ChatType {
            return runCatching { entries[proto.ordinal] }.getOrNull() ?: Unknown
        }
    }
}

sealed interface ConversationType {
    data object TipChat: ConversationType
}

/**
 * An ID that can be referenced to the source of the exchange of Kin
 */
sealed interface Reference {
    data object NoneSet: Reference
    data class IntentId(val id: ID): Reference
    data class Signature(val signature: SolanaKeysSignature): Reference

    companion object {
        operator fun invoke(proto: ExchangeDataContent): Reference {
            return when (proto.referenceCase) {
                ReferenceCase.INTENT -> IntentId(proto.intent.toByteArray().toList())
                ReferenceCase.SIGNATURE -> Signature(SolanaKeysSignature(proto.signature.toByteArray().toList()))
                ReferenceCase.REFERENCE_NOT_SET -> NoneSet
                null -> NoneSet
            }
        }
    }
}


