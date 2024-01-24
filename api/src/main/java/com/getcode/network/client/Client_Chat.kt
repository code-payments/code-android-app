package com.getcode.network.client

import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.model.Chat
import com.getcode.model.ChatMessage
import com.getcode.model.ID
import com.getcode.network.repository.decodeBase64
import com.getcode.network.repository.encodeBase64
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.rx3.asFlow
import timber.log.Timber

suspend fun Client.fetchChats(owner: KeyPair): Result<List<Chat>> {
    return chatService.fetchChats(owner)
        .onSuccess {
            Timber.d("chats fetched=${it.count()}")
        }.onFailure {
            it.printStackTrace()
        }
}

suspend fun Client.fetchMessagesFor(owner: KeyPair, chatId: ID) : Result<List<ChatMessage>> {
    return chatService.fetchMessagesFor(owner, chatId)
        .onSuccess {
            Timber.d("messages fetched=${it.count()} for ${chatId.toByteArray().encodeBase64()}")
            Timber.d("start=${it.minOf { it.date }}, end=${it.maxOf { it.date }}")
        }.onFailure {
            Timber.e(t = it, "Failed fetching messages.")
        }
}