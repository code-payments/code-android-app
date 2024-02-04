package com.getcode.network.client

import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.model.Chat
import com.getcode.model.ChatMessage
import com.getcode.model.Cursor
import com.getcode.model.ID
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

suspend fun Client.fetchMessagesFor(owner: KeyPair, chatId: ID, cursor: Cursor? = null, limit: Int? = null) : Result<List<ChatMessage>> {
    return chatService.fetchMessagesFor(owner, chatId, cursor, limit)
        .onSuccess {
            Timber.d("messages fetched=${it.count()} for ${chatId.toByteArray().encodeBase64()}")
            Timber.d("start=${it.minOf { it.dateMillis }}, end=${it.maxOf { it.dateMillis }}")
        }.onFailure {
            Timber.e(t = it, "Failed fetching messages.")
        }
}