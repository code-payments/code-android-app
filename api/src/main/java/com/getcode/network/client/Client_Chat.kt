package com.getcode.network.client

import com.codeinc.gen.chat.v2.ChatService
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.manager.SessionManager
import com.getcode.model.Cursor
import com.getcode.model.Domain
import com.getcode.model.ID
import com.getcode.model.MessageStatus
import com.getcode.model.chat.Chat
import com.getcode.model.chat.ChatMessage
import com.getcode.model.chat.ConversationEntity
import com.getcode.model.chat.NotificationCollectionEntity
import com.getcode.model.chat.isV2
import com.getcode.network.core.BidirectionalStreamReference
import com.getcode.network.repository.base58
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import timber.log.Timber
import java.util.UUID

typealias ChatMessageStreamReference = BidirectionalStreamReference<ChatService.StreamChatEventsRequest, ChatService.StreamChatEventsResponse>

data class ChatFetchExceptions(val errors: List<Throwable>): Throwable()

suspend fun Client.fetchChats(owner: KeyPair): Result<List<Chat>> {
    val v2Chats = fetchV2Chats(owner)
    val v1Chats = fetchV1Chats(owner)

    if (v2Chats.isSuccess || v1Chats.isSuccess) {
        val chats = (v1Chats.getOrNull().orEmpty() + v2Chats.getOrNull().orEmpty())
            .sortedByDescending { it.lastMessageMillis }
            .distinctBy { it.id }

        return Result.success(chats)
    } else {
        val errors: List<Throwable> =
            listOfNotNull(v1Chats.exceptionOrNull(), v2Chats.exceptionOrNull())
        return Result.failure(ChatFetchExceptions(errors))
    }
}

suspend fun Client.fetchV1Chats(owner: KeyPair): Result<List<NotificationCollectionEntity>> {
    val v1Chats = chatServiceV1.fetchChats(owner)
        .onSuccess {
            Timber.d("v1 chats fetched=${it.count()}")
        }.onFailure {
            trace("Failed fetching chats from V1", type = TraceType.Error)
        }

    return v1Chats
        .map { chats ->
            chats.sortedByDescending { it.lastMessageMillis }
            .distinctBy { it.id }
        }
}

suspend fun Client.fetchV2Chats(owner: KeyPair): Result<List<ConversationEntity>> {
    val v2Chats = chatServiceV2.fetchChats(owner)
        .onSuccess {
            Timber.d("v2 chats fetched=${it.count()}")
        }.onFailure {
            trace("Failed fetching chats from V2", type = TraceType.Error)
        }

    return v2Chats
        .map { chats ->
            chats.sortedByDescending { it.lastMessageMillis }
                .distinctBy { it.id }
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
    memberId: UUID? = null,
    status: MessageStatus = MessageStatus.Read,
): Result<Unit> {
    return if (chat.isV2) {
        memberId ?: return Result.failure(Throwable("member ID was not provided"))
        chatServiceV2.advancePointer(owner, chat.id, memberId, to, status)
    } else {
        chatServiceV1.advancePointer(owner, chat.id, to, status)
    }
}