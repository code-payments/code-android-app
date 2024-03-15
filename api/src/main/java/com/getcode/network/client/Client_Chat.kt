package com.getcode.network.client

import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.manager.SessionManager
import com.getcode.model.Chat
import com.getcode.model.ChatMessage
import com.getcode.model.Cursor
import com.getcode.model.Domain
import com.getcode.model.ID
import com.getcode.model.Title
import com.getcode.network.repository.encodeBase64
import timber.log.Timber

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

suspend fun Client.setSubscriptionState(owner: KeyPair, chatId: ID, subscribed: Boolean): Result<Boolean> {
    return chatService.setSubscriptionState(owner, chatId, subscribed)
}

suspend fun Client.fetchMessagesFor(owner: KeyPair, chat: Chat, cursor: Cursor? = null, limit: Int? = null) : Result<List<ChatMessage>> {
    return chatService.fetchMessagesFor(owner, chat.id, cursor, limit)
        .map {
            val domain = if (chat.title is Title.Domain) {
                Domain.from(chat.title.value)
            } else {
                null
            } ?: return@map it

            val organizer = SessionManager.getOrganizer() ?: return@map it
            val relationship = organizer.relationshipFor(domain) ?: return@map it

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
            Timber.d("messages fetched=${it.count()} for ${chat.id.toByteArray().encodeBase64()}")
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