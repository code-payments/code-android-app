package com.getcode.oct24.internal.network.service

import com.codeinc.flipchat.gen.messaging.v1.MessagingService
import com.codeinc.flipchat.gen.messaging.v1.MessagingService.AdvancePointerResponse
import com.codeinc.flipchat.gen.messaging.v1.MessagingService.GetMessagesResponse
import com.codeinc.flipchat.gen.messaging.v1.Model
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.model.ID
import com.getcode.model.chat.MessageStatus
import com.getcode.services.model.chat.OutgoingMessageContent
import com.getcode.model.description
import com.getcode.oct24.annotations.FcNetworkOracle
import com.getcode.oct24.internal.network.api.MessagingApi
import com.getcode.oct24.internal.network.core.NetworkOracle
import com.getcode.oct24.domain.model.query.QueryOptions
import com.getcode.utils.ErrorUtils
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import javax.inject.Inject
import kotlin.coroutines.resume

class MessagingService @Inject constructor(
    private val api: MessagingApi,
    @FcNetworkOracle private val networkOracle: NetworkOracle,
) {
    suspend fun getMessages(
        owner: KeyPair,
        chatId: ID,
        queryOptions: QueryOptions = QueryOptions(),
    ): Result<List<Model.Message>> {
        return try {
            networkOracle.managedRequest(
                api.getMessages(owner = owner, chatId = chatId, queryOptions = queryOptions)
            ).map { response ->
                when (response.result) {
                    GetMessagesResponse.Result.OK -> {
                        Result.success(response.messagesList)
                    }

                    GetMessagesResponse.Result.UNRECOGNIZED -> {
                        val error = GetMessagesError.Unrecognized
                        Timber.e(t = error)
                        Result.failure(error)
                    }

                    GetMessagesResponse.Result.DENIED -> {
                        val error = GetMessagesError.Denied
                        Timber.e(t = error)
                        Result.failure(error)
                    }

                    else -> {
                        val error = GetMessagesError.Other()
                        Timber.e(t = error)
                        Result.failure(error)
                    }
                }
            }.first()
        } catch (e: Exception) {
            val error = GetMessagesError.Other(cause = e)
            Result.failure(error)
        }
    }

    suspend fun sendMessage(
        owner: KeyPair,
        chatId: ID,
        content: OutgoingMessageContent,
    ): Result<Model.Message> = suspendCancellableCoroutine { cont ->
        try {
            api.sendMessage(
                owner = owner,
                chatId = chatId,
                content = content,
                observer = object : StreamObserver<MessagingService.SendMessageResponse> {
                    override fun onNext(value: MessagingService.SendMessageResponse?) {
                        val requestResult = value?.result
                        if (requestResult == null) {
                            trace(
                                message = "Messaging SendMessage Server returned empty message. This is unexpected.",
                                type = TraceType.Error
                            )
                            return
                        }

                        val result = when (requestResult) {
                            MessagingService.SendMessageResponse.Result.OK -> {
                                trace("Chat message sent =: ${value.message.messageId.value.toList().description}")
                                Result.success(value.message)
                            }

                            MessagingService.SendMessageResponse.Result.DENIED -> {
                                val error = SendMessageError.Denied
                                Timber.e(t = error)
                                Result.failure(error)
                            }

                            MessagingService.SendMessageResponse.Result.INVALID_CONTENT_TYPE -> {
                                val error = SendMessageError.InvalidContentType
                                Timber.e(t = error)
                                Result.failure(error)
                            }

                            MessagingService.SendMessageResponse.Result.UNRECOGNIZED -> {
                                val error = SendMessageError.Unrecognized
                                Timber.e(t = error)
                                Result.failure(error)
                            }

                            else -> {
                                val error = SendMessageError.Other()
                                Timber.e(t = error)
                                Result.failure(error)
                            }
                        }

                        cont.resume(result)
                    }

                    override fun onError(t: Throwable?) {
                        val error = SendMessageError.Other(t)
                        Timber.e(t = error)
                        cont.resume(Result.failure(error))
                    }

                    override fun onCompleted() = Unit
                }
            )
        } catch (e: Exception) {
            ErrorUtils.handleError(e)
            cont.resume(Result.failure(e))
        }
    }

    suspend fun advancePointer(
        owner: KeyPair,
        chatId: ID,
        to: ID,
        status: MessageStatus,
    ): Result<Unit> {
        return try {
            networkOracle.managedRequest(api.advancePointer(owner, chatId, to, status))
                .map { response ->
                    when (response.result) {
                        AdvancePointerResponse.Result.OK -> {
                            Result.success(Unit)
                        }

                        AdvancePointerResponse.Result.UNRECOGNIZED -> {
                            val error = AdvancePointerError.Unrecognized
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        AdvancePointerResponse.Result.DENIED -> {
                            val error = AdvancePointerError.Denied
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        else -> {
                            val error = AdvancePointerError.Other()
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                    }
                }.first()
        } catch (e: Exception) {
            val error = AdvancePointerError.Other(cause = e)
            Result.failure(error)
        }
    }

    suspend fun notifyIsTyping(
        owner: KeyPair,
        chatId: ID,
        isTyping: Boolean
    ): Result<Unit> = suspendCancellableCoroutine { cont ->
        try {
            api.notifyIsTyping(
                owner,
                chatId,
                isTyping,
                observer = object : StreamObserver<MessagingService.NotifyIsTypingResponse> {
                    override fun onNext(value: MessagingService.NotifyIsTypingResponse?) {
                        val requestResult = value?.result
                        if (requestResult == null) {
                            trace(
                                message = "Messaging NotifyTyping Server returned empty message. This is unexpected.",
                                type = TraceType.Error
                            )
                            return
                        }

                        val result = when (requestResult) {
                            MessagingService.NotifyIsTypingResponse.Result.OK -> Result.success(Unit)
                            MessagingService.NotifyIsTypingResponse.Result.DENIED -> {
                                val error = TypingChangeError.Denied
                                Timber.e(t = error)
                                Result.failure(error)
                            }

                            MessagingService.NotifyIsTypingResponse.Result.UNRECOGNIZED -> {
                                val error = TypingChangeError.Unrecognized
                                Timber.e(t = error)
                                Result.failure(error)
                            }

                            else -> {
                                val error = TypingChangeError.Other()
                                Timber.e(t = error)
                                Result.failure(error)
                            }
                        }

                        cont.resume(result)
                    }

                    override fun onError(t: Throwable?) {
                        val error = TypingChangeError.Other(cause = t)
                        Timber.e(t = error)
                        cont.resume(Result.failure(error))
                    }

                    override fun onCompleted() = Unit
                }
            )
        } catch (e: Exception) {
            val error = TypingChangeError.Other(cause = e)
            Timber.e(t = error)
            cont.resume(Result.failure(error))
        }
    }

    sealed class GetMessagesError : Throwable() {
        data object Unrecognized : GetMessagesError()
        data object Denied : GetMessagesError()
        data class Other(override val cause: Throwable? = null) : GetMessagesError()
    }

    sealed class AdvancePointerError : Throwable() {
        data object Unrecognized : AdvancePointerError()
        data object Denied : AdvancePointerError()
        data class Other(override val cause: Throwable? = null) : AdvancePointerError()
    }

    sealed class TypingChangeError : Throwable() {
        data object Unrecognized : AdvancePointerError()
        data object Denied : AdvancePointerError()
        data class Other(override val cause: Throwable? = null) : AdvancePointerError()
    }

    sealed class SendMessageError : Throwable() {
        data object Unrecognized : GetMessagesError()
        data object Denied : GetMessagesError()
        data object InvalidContentType : GetMessagesError()
        data class Other(override val cause: Throwable? = null) : GetMessagesError()
    }
}