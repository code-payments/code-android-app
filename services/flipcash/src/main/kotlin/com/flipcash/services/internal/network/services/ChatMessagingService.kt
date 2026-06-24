package com.flipcash.services.internal.network.services

import com.codeinc.flipcash.gen.messaging.v1.MessagingService as RpcMessagingService
import com.codeinc.flipcash.gen.messaging.v1.Model as MessagingModel
import com.flipcash.services.internal.network.api.ChatMessagingApi
import com.flipcash.services.internal.network.extensions.toEmojiReaction
import com.flipcash.services.internal.network.extensions.toReactionSummary
import com.flipcash.services.internal.network.extensions.toReactor
import com.flipcash.services.models.AddReactionError
import com.flipcash.services.models.AdvancePointerError
import com.flipcash.services.models.DeleteMessageError
import com.flipcash.services.models.EditMessageError
import com.flipcash.services.models.GetDeltaError
import com.flipcash.services.models.GetReactionSummariesError
import com.flipcash.services.models.GetReactionSummaryError
import com.flipcash.services.models.GetReactorsError
import com.flipcash.services.models.SendMessageError
import com.flipcash.services.models.GetMessageError
import com.flipcash.services.models.GetMessagesError
import com.flipcash.services.models.NotifyIsTypingError
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.RemoveReactionError
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ClientMessageId
import com.flipcash.services.models.chat.EmojiReaction
import com.flipcash.services.models.chat.Emoji
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.PointerType
import com.flipcash.services.models.chat.ReactionSummary
import com.flipcash.services.models.chat.Reactor
import com.flipcash.services.models.chat.TypingState
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.network.extensions.foldWithSuppression
import com.getcode.opencode.utils.toValidationOrElse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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

    fun getDelta(
        owner: KeyPair,
        chatId: ChatId,
        afterSequence: Long,
    ): Flow<Result<GetDeltaResult>> {
        return api.getDelta(owner, chatId, afterSequence).map { response ->
            when (response.result) {
                RpcMessagingService.GetDeltaResponse.Result.OK -> Result.success(
                    GetDeltaResult(
                        messages = if (response.hasMessages()) response.messages.messagesList else emptyList(),
                        latestSequence = response.latestSequence,
                        checkpointSequence = response.checkpointSequence,
                    )
                )
                RpcMessagingService.GetDeltaResponse.Result.DENIED -> Result.failure(GetDeltaError.Denied())
                RpcMessagingService.GetDeltaResponse.Result.RESET_REQUIRED -> Result.failure(GetDeltaError.ResetRequired())
                RpcMessagingService.GetDeltaResponse.Result.UNRECOGNIZED -> Result.failure(GetDeltaError.Unrecognized())
                else -> Result.failure(GetDeltaError.Other())
            }
        }
    }

    suspend fun editMessage(
        owner: KeyPair,
        chatId: ChatId,
        messageId: Long,
        content: List<MessageContent>,
        expectedEventSequence: Long,
    ): Result<MessagingModel.Message> {
        return runCatching {
            api.editMessage(owner, chatId, messageId, content, expectedEventSequence)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    RpcMessagingService.EditMessageResponse.Result.OK -> Result.success(response.message)
                    RpcMessagingService.EditMessageResponse.Result.DENIED -> Result.failure(EditMessageError.Denied())
                    RpcMessagingService.EditMessageResponse.Result.MESSAGE_NOT_FOUND -> Result.failure(EditMessageError.MessageNotFound())
                    RpcMessagingService.EditMessageResponse.Result.CANNOT_EDIT -> Result.failure(EditMessageError.CannotEdit())
                    RpcMessagingService.EditMessageResponse.Result.CONFLICT -> Result.failure(EditMessageError.Conflict())
                    RpcMessagingService.EditMessageResponse.Result.UNRECOGNIZED -> Result.failure(EditMessageError.Unrecognized())
                    else -> Result.failure(EditMessageError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { EditMessageError.Other(cause = it) })
            }
        )
    }

    suspend fun deleteMessage(
        owner: KeyPair,
        chatId: ChatId,
        messageId: Long,
        expectedEventSequence: Long,
    ): Result<MessagingModel.Message> {
        return runCatching {
            api.deleteMessage(owner, chatId, messageId, expectedEventSequence)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    RpcMessagingService.DeleteMessageResponse.Result.OK -> Result.success(response.message)
                    RpcMessagingService.DeleteMessageResponse.Result.DENIED -> Result.failure(DeleteMessageError.Denied())
                    RpcMessagingService.DeleteMessageResponse.Result.MESSAGE_NOT_FOUND -> Result.failure(DeleteMessageError.MessageNotFound())
                    RpcMessagingService.DeleteMessageResponse.Result.CANNOT_DELETE -> Result.failure(DeleteMessageError.CannotDelete())
                    RpcMessagingService.DeleteMessageResponse.Result.CONFLICT -> Result.failure(DeleteMessageError.Conflict())
                    RpcMessagingService.DeleteMessageResponse.Result.UNRECOGNIZED -> Result.failure(DeleteMessageError.Unrecognized())
                    else -> Result.failure(DeleteMessageError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { DeleteMessageError.Other(cause = it) })
            }
        )
    }

    suspend fun addReaction(
        owner: KeyPair,
        chatId: ChatId,
        messageId: Long,
        emoji: Emoji,
    ): Result<EmojiReaction> {
        return runCatching {
            api.addReaction(owner, chatId, messageId, emoji)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    RpcMessagingService.AddReactionResponse.Result.OK -> Result.success(response.reaction.toEmojiReaction())
                    RpcMessagingService.AddReactionResponse.Result.DENIED -> Result.failure(AddReactionError.Denied())
                    RpcMessagingService.AddReactionResponse.Result.MESSAGE_NOT_FOUND -> Result.failure(AddReactionError.MessageNotFound())
                    RpcMessagingService.AddReactionResponse.Result.CANNOT_REACT -> Result.failure(AddReactionError.CannotReact())
                    RpcMessagingService.AddReactionResponse.Result.TOO_MANY_REACTION_TYPES -> Result.failure(AddReactionError.TooManyReactionTypes())
                    RpcMessagingService.AddReactionResponse.Result.UNRECOGNIZED -> Result.failure(AddReactionError.Unrecognized())
                    else -> Result.failure(AddReactionError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { AddReactionError.Other(cause = it) })
            }
        )
    }

    suspend fun removeReaction(
        owner: KeyPair,
        chatId: ChatId,
        messageId: Long,
        emoji: Emoji,
    ): Result<EmojiReaction> {
        return runCatching {
            api.removeReaction(owner, chatId, messageId, emoji)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    RpcMessagingService.RemoveReactionResponse.Result.OK -> Result.success(response.reaction.toEmojiReaction())
                    RpcMessagingService.RemoveReactionResponse.Result.DENIED -> Result.failure(RemoveReactionError.Denied())
                    RpcMessagingService.RemoveReactionResponse.Result.MESSAGE_NOT_FOUND -> Result.failure(RemoveReactionError.MessageNotFound())
                    RpcMessagingService.RemoveReactionResponse.Result.UNRECOGNIZED -> Result.failure(RemoveReactionError.Unrecognized())
                    else -> Result.failure(RemoveReactionError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { RemoveReactionError.Other(cause = it) })
            }
        )
    }

    suspend fun getReactors(
        owner: KeyPair,
        chatId: ChatId,
        messageId: Long,
        emoji: Emoji,
        queryOptions: QueryOptions,
    ): Result<GetReactorsResult> {
        return runCatching {
            api.getReactors(owner, chatId, messageId, emoji, queryOptions)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    RpcMessagingService.GetReactorsResponse.Result.OK -> Result.success(
                        GetReactorsResult(
                            reactors = response.reactorsList.map { it.toReactor() },
                            hasMore = response.hasMore,
                        )
                    )
                    RpcMessagingService.GetReactorsResponse.Result.DENIED -> Result.failure(GetReactorsError.Denied())
                    RpcMessagingService.GetReactorsResponse.Result.MESSAGE_NOT_FOUND -> Result.failure(GetReactorsError.MessageNotFound())
                    RpcMessagingService.GetReactorsResponse.Result.UNRECOGNIZED -> Result.failure(GetReactorsError.Unrecognized())
                    else -> Result.failure(GetReactorsError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { GetReactorsError.Other(cause = it) })
            }
        )
    }

    suspend fun getReactionSummary(
        owner: KeyPair,
        chatId: ChatId,
        messageId: Long,
    ): Result<ReactionSummary> {
        return runCatching {
            api.getReactionSummary(owner, chatId, messageId)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    RpcMessagingService.GetReactionSummaryResponse.Result.OK -> Result.success(response.summary.toReactionSummary())
                    RpcMessagingService.GetReactionSummaryResponse.Result.DENIED -> Result.failure(GetReactionSummaryError.Denied())
                    RpcMessagingService.GetReactionSummaryResponse.Result.MESSAGE_NOT_FOUND -> Result.failure(GetReactionSummaryError.MessageNotFound())
                    RpcMessagingService.GetReactionSummaryResponse.Result.UNRECOGNIZED -> Result.failure(GetReactionSummaryError.Unrecognized())
                    else -> Result.failure(GetReactionSummaryError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { GetReactionSummaryError.Other(cause = it) })
            }
        )
    }

    suspend fun getReactionSummaries(
        owner: KeyPair,
        chatId: ChatId,
        queryOptions: QueryOptions,
    ): Result<List<ReactionSummary>> {
        return runCatching {
            api.getReactionSummaries(owner, chatId, queryOptions)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    RpcMessagingService.GetReactionSummariesResponse.Result.OK ->
                        Result.success(response.summariesList.map { it.toReactionSummary() })
                    RpcMessagingService.GetReactionSummariesResponse.Result.DENIED -> Result.failure(GetReactionSummariesError.Denied())
                    RpcMessagingService.GetReactionSummariesResponse.Result.UNRECOGNIZED -> Result.failure(GetReactionSummariesError.Unrecognized())
                    else -> Result.failure(GetReactionSummariesError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { GetReactionSummariesError.Other(cause = it) })
            }
        )
    }
}

data class GetDeltaResult(
    val messages: List<MessagingModel.Message>,
    val latestSequence: Long,
    val checkpointSequence: Long,
)

data class GetReactorsResult(
    val reactors: List<Reactor>,
    val hasMore: Boolean,
)
