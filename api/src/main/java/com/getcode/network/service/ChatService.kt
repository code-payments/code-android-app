package com.getcode.network.service

import com.codeinc.gen.chat.v1.ChatService
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.mapper.ChatMessageMapper
import com.getcode.mapper.ChatMetadataMapper
import com.getcode.model.Chat
import com.getcode.model.ChatMessage
import com.getcode.model.Cursor
import com.getcode.model.ID
import com.getcode.network.api.ChatApi
import com.getcode.network.core.NetworkOracle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import kotlin.reflect.KClass

/**
 * Abstraction layer to handle [ChatApi] request results and map to domain models
 */
class ChatService @Inject constructor(
    private val api: ChatApi,
    private val chatMapper: ChatMetadataMapper,
    private val messageMapper: ChatMessageMapper,
    private val networkOracle: NetworkOracle,
) {

    fun observeChats(owner: KeyPair): Flow<Result<List<Chat>>> {
        return networkOracle.managedRequest(api.fetchChats(owner))
            .map { response ->
                when (response.result) {
                    ChatService.GetChatsResponse.Result.OK -> {
                        Result.success(response.chatsList.map(chatMapper::map))
                    }

                    ChatService.GetChatsResponse.Result.NOT_FOUND -> {
                        val error = Throwable("Error: chats not found for owner")
                        Timber.e(t = error)
                        Result.failure(error)
                    }
                    ChatService.GetChatsResponse.Result.UNRECOGNIZED -> {
                        val error = Throwable("Error: Unrecognized request.")
                        Timber.e(t = error)
                        Result.failure(error)
                    }
                    else -> {
                        val error = Throwable("Error: Unknown")
                        Timber.e(t = error)
                        Result.failure(error)
                    }
                }
            }
    }
    @Throws(NoSuchElementException::class)
    suspend fun fetchChats(owner: KeyPair): Result<List<Chat>> {
        return observeChats(owner).first()
    }

    suspend fun fetchMessagesFor(owner: KeyPair, chatId: ID, cursor: Cursor? = null, limit: Int? = null): Result<List<ChatMessage>> {
        return networkOracle.managedRequest(api.fetchChatMessages(owner, chatId, cursor, limit))
            .map { response ->
                when (response.result) {
                    ChatService.GetMessagesResponse.Result.OK -> {
                        Result.success(response.messagesList.map(messageMapper::map))
                    }

                    ChatService.GetMessagesResponse.Result.NOT_FOUND -> {
                        val error = Throwable("Error: messages not found for chat $chatId")
                        Timber.e(t = error)
                        Result.failure(error)
                    }
                    ChatService.GetMessagesResponse.Result.UNRECOGNIZED -> {
                        val error = Throwable("Error: Unrecognized request.")
                        Timber.e(t = error)
                        Result.failure(error)
                    }
                    else -> {
                        val error = Throwable("Error: Unknown")
                        Timber.e(t = error)
                        Result.failure(error)
                    }
                }
            }.first()
    }
}