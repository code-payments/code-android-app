package com.flipcash.services.internal.network.services

import com.codeinc.flipcash.gen.messaging.v1.MessagingService as RpcMessagingService
import com.codeinc.flipcash.gen.messaging.v1.Model as MessagingModel
import com.flipcash.services.internal.network.api.ChatMessagingApi
import com.flipcash.services.models.AdvancePointerError
import com.flipcash.services.models.SendMessageError
import com.flipcash.services.models.GetMessageError
import com.flipcash.services.models.GetMessagesError
import com.flipcash.services.models.NotifyIsTypingError
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ClientMessageId
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.PointerType
import com.flipcash.services.models.chat.TypingState
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.network.extensions.foldWithSuppression
import com.getcode.opencode.utils.toValidationOrElse
import javax.inject.Inject

internal class ChatMessagingService @Inject constructor(
    private val api: ChatMessagingApi,
) {
    suspend fun getMessage(
        owner: KeyPair,
        chatId: ChatId,
        messageId: Long,
    ): Result<MessagingModel.Message> {
        return runCatching {
            api.getMessage(owner, chatId, messageId)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    RpcMessagingService.GetMessageResponse.Result.OK -> Result.success(response.message)
                    RpcMessagingService.GetMessageResponse.Result.DENIED -> Result.failure(GetMessageError.Denied())
                    RpcMessagingService.GetMessageResponse.Result.NOT_FOUND -> Result.failure(GetMessageError.NotFound())
                    RpcMessagingService.GetMessageResponse.Result.UNRECOGNIZED -> Result.failure(GetMessageError.Unrecognized())
                    else -> Result.failure(GetMessageError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { GetMessageError.Other(cause = it) })
            }
        )
    }

    suspend fun getMessages(
        owner: KeyPair,
        chatId: ChatId,
        queryOptions: QueryOptions,
    ): Result<List<MessagingModel.Message>> {
        return runCatching {
            api.getMessages(owner, chatId, queryOptions)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    RpcMessagingService.GetMessagesResponse.Result.OK ->
                        Result.success(if (response.hasMessages()) response.messages.messagesList else emptyList())
                    RpcMessagingService.GetMessagesResponse.Result.DENIED -> Result.failure(GetMessagesError.Denied())
                    RpcMessagingService.GetMessagesResponse.Result.NOT_FOUND -> Result.failure(GetMessagesError.NotFound())
                    RpcMessagingService.GetMessagesResponse.Result.UNRECOGNIZED -> Result.failure(GetMessagesError.Unrecognized())
                    else -> Result.failure(GetMessagesError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { GetMessagesError.Other(cause = it) })
            }
        )
    }

    suspend fun getMessagesByIds(
        owner: KeyPair,
        chatId: ChatId,
        messageIds: List<Long>,
    ): Result<List<MessagingModel.Message>> {
        return runCatching {
            api.getMessagesByIds(owner, chatId, messageIds)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    RpcMessagingService.GetMessagesResponse.Result.OK ->
                        Result.success(if (response.hasMessages()) response.messages.messagesList else emptyList())
                    RpcMessagingService.GetMessagesResponse.Result.DENIED -> Result.failure(GetMessagesError.Denied())
                    RpcMessagingService.GetMessagesResponse.Result.NOT_FOUND -> Result.failure(GetMessagesError.NotFound())
                    RpcMessagingService.GetMessagesResponse.Result.UNRECOGNIZED -> Result.failure(GetMessagesError.Unrecognized())
                    else -> Result.failure(GetMessagesError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { GetMessagesError.Other(cause = it) })
            }
        )
    }

    suspend fun sendMessage(
        owner: KeyPair,
        chatId: ChatId,
        content: List<MessageContent>,
        clientMessageId: ClientMessageId,
    ): Result<MessagingModel.Message> {
        return runCatching {
            api.sendMessage(owner, chatId, content, clientMessageId)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    RpcMessagingService.SendMessageResponse.Result.OK -> Result.success(response.message)
                    RpcMessagingService.SendMessageResponse.Result.DENIED -> Result.failure(SendMessageError.Denied())
                    RpcMessagingService.SendMessageResponse.Result.UNRECOGNIZED -> Result.failure(SendMessageError.Unrecognized())
                    else -> Result.failure(SendMessageError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { SendMessageError.Other(cause = it) })
            }
        )
    }

    suspend fun advancePointer(
        owner: KeyPair,
        chatId: ChatId,
        pointerType: PointerType,
        messageId: Long,
    ): Result<Unit> {
        return runCatching {
            api.advancePointer(owner, chatId, pointerType, messageId)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    RpcMessagingService.AdvancePointerResponse.Result.OK -> Result.success(Unit)
                    RpcMessagingService.AdvancePointerResponse.Result.DENIED -> Result.failure(AdvancePointerError.Denied())
                    RpcMessagingService.AdvancePointerResponse.Result.MESSAGE_NOT_FOUND -> Result.failure(AdvancePointerError.MessageNotFound())
                    RpcMessagingService.AdvancePointerResponse.Result.UNRECOGNIZED -> Result.failure(AdvancePointerError.Unrecognized())
                    else -> Result.failure(AdvancePointerError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { AdvancePointerError.Other(cause = it) })
            }
        )
    }

    suspend fun notifyIsTyping(
        owner: KeyPair,
        chatId: ChatId,
        state: TypingState,
    ): Result<Unit> {
        return runCatching {
            api.notifyIsTyping(owner, chatId, state)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    RpcMessagingService.NotifyIsTypingResponse.Result.OK -> Result.success(Unit)
                    RpcMessagingService.NotifyIsTypingResponse.Result.DENIED -> Result.failure(NotifyIsTypingError.Denied())
                    RpcMessagingService.NotifyIsTypingResponse.Result.UNRECOGNIZED -> Result.failure(NotifyIsTypingError.Unrecognized())
                    else -> Result.failure(NotifyIsTypingError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { NotifyIsTypingError.Other(cause = it) })
            }
        )
    }
}
