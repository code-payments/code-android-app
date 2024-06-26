package com.getcode.network.client

import com.codeinc.gen.chat.v2.ChatService
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.manager.SessionManager
import com.getcode.model.chat.Chat
import com.getcode.model.chat.ChatMessage
import com.getcode.model.Cursor
import com.getcode.model.Domain
import com.getcode.model.ID
import com.getcode.model.chat.Title
import com.getcode.model.chat.isV2
import com.getcode.network.core.BidirectionalStreamReference
import com.getcode.network.repository.base58
import kotlinx.coroutines.CoroutineScope
import timber.log.Timber

typealias ChatMessageStreamReference = BidirectionalStreamReference<ChatService.StreamChatEventsRequest, ChatService.StreamChatEventsResponse>

suspend fun Client.fetchChats(owner: KeyPair, useV2: Boolean = false): Result<List<Chat>> {
    return if (useV2) {
        chatServiceV2.fetchChats(owner)
            .onSuccess {
                Timber.d("chats fetched=${it.count()}")
            }.onFailure {
                it.printStackTrace()
            }
    } else {
        chatServiceV1.fetchChats(owner)
            .onSuccess {
                Timber.d("chats fetched=${it.count()}")
            }.onFailure {
                it.printStackTrace()
            }
    }
}

suspend fun Client.setMuted(owner: KeyPair, chat: Chat, muted: Boolean): Result<Boolean> {
    return if (chat.isV2) {
        chatServiceV2.setMuteState(owner, chat.id, muted)
    } else {
        chatServiceV1.setMuteState(owner, chat.id, muted)
    }
}

suspend fun Client.setSubscriptionState(
    owner: KeyPair,
    chat: Chat,
    subscribed: Boolean
): Result<Boolean> {
    return if (chat.isV2) {
        chatServiceV2.setSubscriptionState(owner, chat.id, subscribed)
    } else {
        chatServiceV1.setSubscriptionState(owner, chat.id, subscribed)
    }
}

suspend fun Client.fetchMessagesFor(
    owner: KeyPair,
    chat: Chat,
    cursor: Cursor? = null,
    limit: Int? = null
): Result<List<ChatMessage>> {
    val result = if (chat.isV2) {
        chatServiceV2.fetchMessagesFor(owner, chat, cursor, limit)
    } else {
        chatServiceV1.fetchMessagesFor(owner, chat, cursor, limit)
    }

    return result
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
            Timber.d("start=${it.minOf { it.dateMillis }}, end=${it.maxOf { it.dateMillis }}")
        }.onFailure {
            Timber.e(t = it, "Failed fetching messages.")
        }
}

suspend fun Client.advancePointer(
    owner: KeyPair,
    chat: Chat,
    to: ID,
): Result<Unit> {
    return if (chat.isV2) {
        chatServiceV2.advancePointer(owner, chat.id, to)
    } else {
        chatServiceV1.advancePointer(owner, chat.id, to)
    }
}

fun Client.openChatStream(
    scope: CoroutineScope,
    chat: Chat,
    memberId: ID,
    owner: KeyPair,
    completion: (Result<List<ChatMessage>>) -> Unit
): ChatMessageStreamReference {
    if (!chat.isV2) throw IllegalArgumentException("Chat is not a V2 Chat")
    return chatServiceV2.openChatStream(
        scope = scope,
        chat = chat,
        memberId = memberId,
        owner = owner,
        completion = completion
    )
}