package com.getcode.network.client

import com.codeinc.gen.chat.v2.ChatService
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.manager.SessionManager
import com.getcode.model.chat.Chat
import com.getcode.model.chat.ChatMessage
import com.getcode.model.Cursor
import com.getcode.model.Domain
import com.getcode.model.ID
import com.getcode.network.core.BidirectionalStreamReference
import com.getcode.network.repository.base58
import kotlinx.coroutines.CoroutineScope
import timber.log.Timber

typealias ChatMessageStreamReference = BidirectionalStreamReference<ChatService.StreamChatEventsRequest, ChatService.StreamChatEventsResponse>


suspend fun Client.fetchChats(owner: KeyPair): Result<List<Chat>> {
    return chatService.fetchChats(owner)
        .onSuccess {
            Timber.d("chats fetched=${it.count()}")
        }.onFailure {
            it.printStackTrace()
        }
}

suspend fun Client.setMuted(owner: KeyPair, chat: ID, muted: Boolean): Result<Boolean> {
    return chatService.setMuteState(owner, chat, muted)
}

suspend fun Client.setSubscriptionState(
    owner: KeyPair,
    chatId: ID,
    subscribed: Boolean
): Result<Boolean> {
    return chatService.setSubscriptionState(owner, chatId, subscribed)
}

suspend fun Client.fetchMessagesFor(
    owner: KeyPair,
    chat: Chat,
    cursor: Cursor? = null,
    limit: Int? = null
): Result<List<ChatMessage>> {
    return chatService.fetchMessagesFor(owner, chat, cursor, limit)
        .mapCatching {
            val organizer = SessionManager.getOrganizer() ?: return@mapCatching it
            val domain = Domain.from(chat.title) ?: return@mapCatching it
            val relationship = organizer.relationshipFor(domain) ?: return@mapCatching it

            val hasEncryptedContent = it.firstOrNull { it.hasEncryptedContent } != null
            if (hasEncryptedContent) {
                it.map { message ->
                    message.decryptingUsing(relationship.getCluster().authority.keyPair)
                }
            } else {
                it
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
    chatId: ID,
    to: ID,
): Result<Unit> {
    return chatService.advancePointer(owner, chatId, to)
}

fun Client.openChatStream(
    scope: CoroutineScope,
    chat: Chat,
    memberId: ID,
    owner: KeyPair,
    completion: (Result<List<ChatMessage>>) -> Unit
): ChatMessageStreamReference {
    return chatServiceV2.openChatStream(
        scope = scope,
        chat = chat,
        memberId = memberId,
        owner = owner,
        completion = completion
    )
}