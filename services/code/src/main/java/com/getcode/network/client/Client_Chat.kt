package com.getcode.network.client

import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.manager.SessionManager
import com.getcode.model.Cursor
import com.getcode.model.Domain
import com.getcode.model.ID
import com.getcode.model.chat.Chat
import com.getcode.model.chat.ChatMessage
import com.getcode.model.chat.MessageStatus
import com.getcode.model.chat.NotificationCollectionEntity
import com.getcode.model.extensions.decryptingUsing
import com.getcode.utils.TraceType
import com.getcode.utils.base58
import com.getcode.utils.trace
import timber.log.Timber

suspend fun Client.fetchChats(owner: KeyPair): Result<List<NotificationCollectionEntity>> {
    val chats = chatService.fetchChats(owner)
        .onSuccess {
            Timber.d("v1 chats fetched=${it.count()}")
        }.onFailure {
            trace(
                "Failed fetching chats from V1",
                type = TraceType.Error
            )
        }

    return chats
        .map { list ->
            list.sortedByDescending { it.lastMessageMillis }
            .distinctBy { it.id }
        }
}

suspend fun Client.setMuted(owner: KeyPair, chat: Chat, muted: Boolean): Result<Boolean> {
    return chatService.setMuteState(owner, chat.id, muted)
}

suspend fun Client.setSubscriptionState(
    owner: KeyPair,
    chat: Chat,
    subscribed: Boolean
): Result<Boolean> {
    return chatService.setSubscriptionState(owner, chat.id, subscribed)
}

suspend fun Client.fetchMessagesFor(
    owner: KeyPair,
    chat: Chat,
    cursor: Cursor? = null,
    limit: Int? = null
): Result<List<ChatMessage>> {
    return chatService.fetchMessagesFor(owner, chat, cursor, limit)
        .mapCatching { messages ->
            val organizer = SessionManager.getOrganizer() ?: return@mapCatching messages
            val domain = Domain.from(chat.title?.value) ?: return@mapCatching messages

            val relationship = organizer.relationshipFor(domain) ?: return@mapCatching messages

            val hasEncryptedContent = messages.firstOrNull { it.hasEncryptedContent } != null
            if (hasEncryptedContent) {
                messages.map { message ->
                    message.decryptingUsing(relationship.getCluster().authority.keyPair)
                }
            } else {
                messages
            }
        }
        .onSuccess {
            Timber.d("messages fetched=${it.count()} for ${chat.id.base58}")
            if (it.isNotEmpty()) {
                Timber.d("start=${it.minOf { it.dateMillis }}, end=${it.maxOf { it.dateMillis }}")
            }
        }.onFailure {
            Timber.e(t = it, "Failed fetching messages.")
        }
}

suspend fun Client.advancePointer(
    owner: KeyPair,
    chat: Chat,
    to: ID,
    status: MessageStatus = MessageStatus.Read,
): Result<Unit> {
    return chatService.advancePointer(owner, chat.id, to, status)
}