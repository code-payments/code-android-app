package com.getcode.opencode.internal.network.services

import com.codeinc.opencode.gen.messaging.v1.MessagingService
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.domain.mapping.MessageMapper
import com.getcode.opencode.internal.network.api.MessagingApi
import com.getcode.opencode.internal.network.core.DEFAULT_STREAM_TIMEOUT
import com.getcode.opencode.internal.network.core.NetworkOracle
import com.getcode.opencode.internal.network.extensions.clientPongWith
import com.getcode.opencode.internal.network.extensions.openMessageStreamRequest
import com.getcode.opencode.internal.network.extensions.toId
import com.getcode.opencode.internal.network.managedApiRequest
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.core.errors.AckMessagesError
import com.getcode.opencode.model.core.errors.PollMessagesError
import com.getcode.opencode.model.core.errors.SendMessageError
import com.getcode.opencode.model.messaging.Message
import com.getcode.opencode.observers.BidirectionalStreamReference
import com.getcode.utils.ErrorUtils
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import com.codeinc.opencode.gen.messaging.v1.MessagingService as RpcMessagingService

typealias OcpMessageStreamReference = BidirectionalStreamReference<RpcMessagingService.OpenMessageStreamWithKeepAliveRequest, RpcMessagingService.OpenMessageStreamWithKeepAliveResponse>

internal class MessagingService @Inject constructor(
    private val api: MessagingApi,
    private val networkOracle: NetworkOracle,
    private val messageMapper: MessageMapper,
) {

    suspend fun openMessageStream(
        rendezvous: KeyPair,
        timeout: Long = DEFAULT_STREAM_TIMEOUT,
        filter: (MessagingService.Message.KindCase) -> Boolean = { true },
    ): Flow<List<Message>> = withTimeout(timeout) {
        coroutineScope {
            api.openMessageStream(rendezvous)
                .map {
                    it.messagesList.filter { filter(it.kindCase) }
                }.filter { it.isNotEmpty() }
                .map { messages -> messages.map { messageMapper.map(it) } }
                .onEach { messages ->
                    ackMessages(rendezvous, messages.map { it.id })
                        .onSuccess {
                            trace(
                                tag = "MessagingService",
                                message = "acked",
                                type = TraceType.Silent
                            )
                        }.onFailure { ErrorUtils.handleError(it) }
                }.filter { it.isNotEmpty() }
                .retry(retries = 10)
        }
    }

    fun openMessageStreamWithKeepAlive(
        scope: CoroutineScope,
        rendezvous: KeyPair,
        onEvent: (Result<List<Message>>) -> Unit,
    ): OcpMessageStreamReference {
        trace("Message Opening stream.")
        val streamReference = OcpMessageStreamReference(scope)
        streamReference.retain()
        streamReference.timeoutHandler = {
            trace("Message Stream timed out")
            openMessageStream(
                rendezvous = rendezvous,
                streamRef = streamReference,
                onEvent = onEvent
            )
        }

        openMessageStream(rendezvous, streamReference, onEvent)

        return streamReference
    }

    private fun openMessageStream(
        rendezvous: KeyPair,
        streamRef: OcpMessageStreamReference,
        onEvent: (Result<List<Message>>) -> Unit
    ) {
        try {
            streamRef.cancel()
            streamRef.stream =
                api.openMessageStreamWithKeepAlive(object :
                    StreamObserver<RpcMessagingService.OpenMessageStreamWithKeepAliveResponse> {
                    override fun onNext(value: RpcMessagingService.OpenMessageStreamWithKeepAliveResponse?) {
                        val result = value?.responseOrPingCase
                        if (result == null) {
                            trace(
                                message = "Message Stream Server sent empty message. This is unexpected.",
                                type = TraceType.Error
                            )
                            return
                        }

                        when (result) {
                            RpcMessagingService.OpenMessageStreamWithKeepAliveResponse.ResponseOrPingCase.RESPONSE -> {
                                onEvent(Result.success(value.response.messagesList.map {
                                    messageMapper.map(
                                        it
                                    )
                                }))
                            }

                            RpcMessagingService.OpenMessageStreamWithKeepAliveResponse.ResponseOrPingCase.PING -> {
                                val stream = streamRef.stream ?: return
                                val request =
                                    RpcMessagingService.OpenMessageStreamWithKeepAliveRequest.newBuilder()
                                        .setPong(clientPongWith(System.currentTimeMillis()))
                                        .build()


                                streamRef.receivedPing(updatedTimeout = value.ping.pingDelay.seconds * 1_000L)
                                stream.onNext(request)
                                trace("Pong Message Stream Server timestamp: ${value.ping.timestamp}")
                            }

                            RpcMessagingService.OpenMessageStreamWithKeepAliveResponse.ResponseOrPingCase.RESPONSEORPING_NOT_SET -> Unit
                        }
                    }

                    override fun onError(t: Throwable?) {
                        val statusException = t as? StatusRuntimeException
                        if (statusException?.status?.code == Status.Code.UNAVAILABLE) {
                            trace("Message Stream Reconnecting keepalive stream...")
                            openMessageStream(
                                rendezvous,
                                streamRef,
                                onEvent
                            )
                        } else {
                            t?.printStackTrace()
                        }
                    }

                    override fun onCompleted() {

                    }
                })

            streamRef.coroutineScope.launch {
                val request = RpcMessagingService.OpenMessageStreamWithKeepAliveRequest.newBuilder()
                    .setRequest(openMessageStreamRequest(rendezvous))
                    .build()

                streamRef.stream?.onNext(request)
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

    suspend fun pollMessages(
        rendezvous: KeyPair,
    ): Result<List<Message>> {
        return networkOracle.managedApiRequest(
            call = { api.pollMessages(rendezvous) },
            handleResponse = { response ->
                Result.success(response.messagesList.map { messageMapper.map(it) })
            },
            onOtherError = { error ->
                Result.failure(PollMessagesError.Other(cause = error))
            }
        )
    }

    suspend fun ackMessages(
        rendezvous: KeyPair,
        messageIds: List<ID> = emptyList(),
    ): Result<Unit> {
        return networkOracle.managedApiRequest(
            call = { api.ackMessages(rendezvous, messageIds) },
            handleResponse = { response ->
                when (response.result) {
                    RpcMessagingService.AckMesssagesResponse.Result.OK -> Result.success(Unit)
                    RpcMessagingService.AckMesssagesResponse.Result.UNRECOGNIZED -> {
                        Result.failure(AckMessagesError.Unrecognized())
                    }

                    else -> Result.failure(AckMessagesError.Other())
                }
            },
            onOtherError = { error ->
                Result.failure(PollMessagesError.Other(cause = error))
            }
        )
    }

    suspend fun sendMessage(
        rendezvous: KeyPair,
        message: Message,
    ): Result<ID> {
        return networkOracle.managedApiRequest(
            call = { api.sendMessage(rendezvous = rendezvous, message = message) },
            handleResponse = { response ->
                when (response.result) {
                    RpcMessagingService.SendMessageResponse.Result.OK -> Result.success(response.messageId.toId())
                    RpcMessagingService.SendMessageResponse.Result.UNRECOGNIZED -> {
                        Result.failure(SendMessageError.Unrecognized())
                    }

                    RpcMessagingService.SendMessageResponse.Result.NO_ACTIVE_STREAM -> {
                        Result.failure(SendMessageError.NoActiveStream())
                    }

                    else -> Result.failure(SendMessageError.Other())
                }
            },
            onOtherError = { error ->
                Result.failure(SendMessageError.Other(cause = error))
            }
        )
    }
}