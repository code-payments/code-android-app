package xyz.flipchat.services.internal.network.service

import com.codeinc.flipchat.gen.common.v1.Flipchat
import com.codeinc.flipchat.gen.messaging.v1.MessagingService
import com.codeinc.flipchat.gen.messaging.v1.MessagingService.AdvancePointerResponse
import com.codeinc.flipchat.gen.messaging.v1.MessagingService.GetMessagesResponse
import com.codeinc.flipchat.gen.messaging.v1.MessagingService.StreamMessagesRequest.Params
import com.codeinc.flipchat.gen.messaging.v1.Model
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.model.ID
import com.getcode.model.chat.MessageStatus
import com.getcode.model.description
import com.getcode.services.model.chat.OutgoingMessageContent
import com.getcode.services.network.core.NetworkOracle
import com.getcode.services.observers.BidirectionalStreamReference
import com.getcode.utils.ErrorUtils
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import com.google.protobuf.Timestamp
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import xyz.flipchat.services.domain.model.query.QueryOptions
import xyz.flipchat.services.internal.network.api.MessagingApi
import xyz.flipchat.services.internal.network.extensions.toChatId
import xyz.flipchat.services.internal.network.extensions.toMessageId
import xyz.flipchat.services.internal.network.utils.authenticate
import javax.inject.Inject
import kotlin.coroutines.resume

typealias ChatMessageStreamReference = BidirectionalStreamReference<MessagingService.StreamMessagesRequest, MessagingService.StreamMessagesResponse>


internal class MessagingService @Inject constructor(
    private val api: MessagingApi,
    private val networkOracle: NetworkOracle,
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
                        val error = GetMessagesError.Unrecognized()
                        Timber.e(t = error)
                        Result.failure(error)
                    }

                    GetMessagesResponse.Result.DENIED -> {
                        val error = GetMessagesError.Denied()
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
                                val error = SendMessageError.Denied()
                                Timber.e(t = error)
                                Result.failure(error)
                            }

                            MessagingService.SendMessageResponse.Result.UNRECOGNIZED -> {
                                val error = SendMessageError.Unrecognized()
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
                            val error = AdvancePointerError.Unrecognized()
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        AdvancePointerResponse.Result.DENIED -> {
                            val error = AdvancePointerError.Denied()
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
                                val error = TypingChangeError.Denied()
                                Timber.e(t = error)
                                Result.failure(error)
                            }

                            MessagingService.NotifyIsTypingResponse.Result.UNRECOGNIZED -> {
                                val error = TypingChangeError.Unrecognized()
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

    fun openMessageStream(
        scope: CoroutineScope,
        owner: KeyPair,
        chatId: ID,
        lastMessageId: suspend () -> ID?,
        onEvent: (Result<Model.Message>) -> Unit
    ): ChatMessageStreamReference {
        trace("Message Opening stream.")
        val streamReference = ChatMessageStreamReference(scope)
        streamReference.retain()
        streamReference.timeoutHandler = {
            trace("Message Stream timed out")
            openMessageStream(
                owner = owner,
                chatId = chatId,
                lastMessageId = lastMessageId,
                reference = streamReference,
                onEvent = onEvent
            )
        }

        openMessageStream(owner, chatId, lastMessageId, streamReference, onEvent)

        return streamReference
    }

    private fun openMessageStream(
        owner: KeyPair,
        chatId: ID,
        lastMessageId: suspend () -> ID?,
        reference: ChatMessageStreamReference,
        onEvent: (Result<Model.Message>) -> Unit
    ) {
        try {
            reference.cancel()
            reference.stream =
                api.streamMessages(object : StreamObserver<MessagingService.StreamMessagesResponse> {
                    override fun onNext(value: MessagingService.StreamMessagesResponse?) {
                        val result = value?.typeCase
                        if (result == null) {
                            trace(
                                message = "Message Stream Server sent empty message. This is unexpected.",
                                type = TraceType.Error
                            )
                            return
                        }

                        when (result) {
                            MessagingService.StreamMessagesResponse.TypeCase.MESSAGES -> {
                                value.messages.messagesList.onEach { update ->
                                    onEvent(Result.success(update))
                                }
                            }

                            MessagingService.StreamMessagesResponse.TypeCase.PING -> {
                                val stream = reference.stream ?: return
                                val request = MessagingService.StreamMessagesRequest.newBuilder()
                                    .setParams(Params.newBuilder().setChatId(chatId.toChatId()))
                                    .setPong(
                                        Flipchat.ClientPong.newBuilder()
                                            .setTimestamp(
                                                Timestamp.newBuilder()
                                                    .setSeconds(System.currentTimeMillis() / 1_000)
                                            )
                                    ).build()

                                reference.receivedPing(updatedTimeout = value.ping.pingDelay.seconds * 1_000L)
                                stream.onNext(request)
                                trace("Pong Message Stream Server timestamp: ${value.ping.timestamp}")
                            }

                            MessagingService.StreamMessagesResponse.TypeCase.TYPE_NOT_SET -> Unit
                            MessagingService.StreamMessagesResponse.TypeCase.ERROR -> {
                                trace(
                                    type = TraceType.Error,
                                    message = "Message Stream hit a snag. ${value.error.code}"
                                )
                            }
                        }
                    }

                    override fun onError(t: Throwable?) {
                        val statusException = t as? StatusRuntimeException
                        if (statusException?.status?.code == Status.Code.UNAVAILABLE) {
                            trace("Message Stream Reconnecting keepalive stream...")
                            openMessageStream(
                                owner,
                                chatId,
                                lastMessageId,
                                reference,
                                onEvent
                            )
                        } else {
                            t?.printStackTrace()
                        }
                    }

                    override fun onCompleted() {

                    }
                })

            reference.scope.launch {
                val request = MessagingService.StreamMessagesRequest.newBuilder()
                    .setParams(
                        Params.newBuilder()
                            .setChatId(chatId.toChatId())
                            .apply {
                                lastMessageId()?.let {
                                    setLastKnownMessageId(it.toMessageId())
                                }
                            }
                            .apply { setAuth(authenticate(owner)) }
                    ).build()

                reference.stream?.onNext(request)
                trace("Message Stream Initiating a connection...")
            }
        } catch (e: Exception) {
            if (e is IllegalStateException && e.message == "call already half-closed") {
                // ignore
            } else {
                ErrorUtils.handleError(e)
            }
        }
    }

    sealed class GetMessagesError : Throwable() {
        class Unrecognized : GetMessagesError()
        class Denied : GetMessagesError()
        data class Other(override val cause: Throwable? = null) : GetMessagesError()
    }

    sealed class AdvancePointerError : Throwable() {
        class Unrecognized : AdvancePointerError()
        class Denied : AdvancePointerError()
        data class Other(override val cause: Throwable? = null) : AdvancePointerError()
    }

    sealed class TypingChangeError : Throwable() {
        class Unrecognized : AdvancePointerError()
        class Denied : AdvancePointerError()
        data class Other(override val cause: Throwable? = null) : AdvancePointerError()
    }

    sealed class SendMessageError : Throwable() {
        class Unrecognized : GetMessagesError()
        class Denied : GetMessagesError()
        class InvalidContentType : GetMessagesError()
        data class Other(override val cause: Throwable? = null) : GetMessagesError()
    }
}