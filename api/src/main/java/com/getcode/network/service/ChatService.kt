package com.getcode.network.service

import com.codeinc.gen.chat.v1.ChatService
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.mapper.ChatMessageMapper
import com.getcode.mapper.ChatMetadataMapper
import com.getcode.model.Chat
import com.getcode.model.ChatMessage
import com.getcode.model.Cursor
import com.getcode.model.ID
import com.getcode.model.description
import com.getcode.network.api.ChatApi
import com.getcode.network.core.NetworkOracle
import com.getcode.utils.ErrorUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject

/**
 * Abstraction layer to handle [ChatApi] request results and map to domain models
 */
@Deprecated("Being replaced with V2")
class ChatService @Inject constructor(
    private val api: ChatApi,
    private val chatMapper: ChatMetadataMapper,
    private val messageMapper: ChatMessageMapper,
    private val networkOracle: NetworkOracle,
) {

    private fun observeChats(owner: KeyPair): Flow<Result<List<Chat>>> {
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
        return runCatching { observeChats(owner).first() }.getOrDefault(Result.success(emptyList()))
    }

    suspend fun setMuteState(owner: KeyPair, chatId: ID, muted: Boolean): Result<Boolean> {
        return try {
            networkOracle.managedRequest(api.setMuteState(owner, chatId, muted))
                .map { response ->
                    when (response.result) {
                        ChatService.SetMuteStateResponse.Result.OK -> {
                            Result.success(muted)
                        }

                        ChatService.SetMuteStateResponse.Result.CHAT_NOT_FOUND -> {
                            val error = Throwable("Error: chat not found for $chatId")
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        ChatService.SetMuteStateResponse.Result.CANT_MUTE -> {
                            val error = Throwable("Error: Unable to change mute state for $chatId.")
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        ChatService.SetMuteStateResponse.Result.UNRECOGNIZED -> {
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
        } catch (e: Exception) {
            ErrorUtils.handleError(e)
            Result.failure(e)
        }
    }

    suspend fun setSubscriptionState(
        owner: KeyPair,
        chatId: ID,
        subscribed: Boolean,
    ): Result<Boolean> {
        return try {
            networkOracle.managedRequest(api.setSubscriptionState(owner, chatId, subscribed))
                .map { response ->
                    when (response.result) {
                        ChatService.SetSubscriptionStateResponse.Result.OK -> {
                            Result.success(subscribed)
                        }

                        ChatService.SetSubscriptionStateResponse.Result.CHAT_NOT_FOUND -> {
                            val error = Throwable("Error: chat not found for $chatId")
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        ChatService.SetSubscriptionStateResponse.Result.CANT_UNSUBSCRIBE -> {
                            val error = Throwable("Error: Unable to change mute state for $chatId.")
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        ChatService.SetSubscriptionStateResponse.Result.UNRECOGNIZED -> {
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
        } catch (e: Exception) {
            ErrorUtils.handleError(e)
            Result.failure(e)
        }
    }

    suspend fun fetchMessagesFor(
        owner: KeyPair,
        chatId: ID,
        cursor: Cursor? = null,
        limit: Int? = null
    ): Result<List<ChatMessage>> {
        return try {
            networkOracle.managedRequest(api.fetchChatMessages(owner, chatId, cursor, limit))
                .map { response ->
                    when (response.result) {
                        ChatService.GetMessagesResponse.Result.OK -> {
                            Result.success(response.messagesList.map(messageMapper::map))
                        }

                        ChatService.GetMessagesResponse.Result.NOT_FOUND -> {
                            val error =
                                Throwable("Error: messages not found for chat ${chatId.description}")
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
        } catch (e: Exception) {
            ErrorUtils.handleError(e)
            Result.failure(e)
        }
    }

    suspend fun advancePointer(
        owner: KeyPair,
        chatId: ID,
        to: ID,
    ): Result<Unit> {
        return try {
            networkOracle.managedRequest(api.advancePointer(owner, chatId, to))
                .map { response ->
                    when (response.result) {
                        ChatService.AdvancePointerResponse.Result.OK -> {
                            Result.success(Unit)
                        }

                        ChatService.AdvancePointerResponse.Result.CHAT_NOT_FOUND -> {
                            val error = Throwable("Error: chat not found $chatId")
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        ChatService.AdvancePointerResponse.Result.MESSAGE_NOT_FOUND -> {
                            val error = Throwable("Error: message not found $to")
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        ChatService.AdvancePointerResponse.Result.UNRECOGNIZED -> {
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
        } catch (e: Exception) {
            ErrorUtils.handleError(e)
            Result.failure(e)
        }
    }
}