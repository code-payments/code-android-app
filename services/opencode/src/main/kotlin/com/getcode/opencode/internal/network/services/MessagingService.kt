package com.getcode.opencode.internal.network.services

import com.codeinc.opencode.gen.messaging.v1.MessagingService
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.bidi.BidirectionalStreamReference
import com.getcode.opencode.internal.bidi.openBidirectionalStream
import com.getcode.opencode.internal.network.api.MessagingApi
import com.getcode.opencode.internal.network.extensions.clientPongWith
import com.getcode.opencode.internal.network.extensions.openMessageStreamRequest
import com.getcode.opencode.internal.network.extensions.toPublicKey
import com.getcode.opencode.model.core.errors.AckMessagesError
import com.getcode.opencode.model.core.errors.PollMessagesError
import com.getcode.opencode.model.core.errors.SendMessageError
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.codeinc.opencode.gen.messaging.v1.MessagingService as RpcMessagingService

typealias OcpMessageStreamReference = BidirectionalStreamReference<RpcMessagingService.OpenMessageStreamWithKeepAliveRequest, RpcMessagingService.OpenMessageStreamWithKeepAliveResponse>

internal class MessagingService @Inject constructor(
    private val api: MessagingApi,
) {
    fun openMessageStreamWithKeepAlive(
        scope: CoroutineScope,
        rendezvous: KeyPair,
        onEvent: (Result<List<RpcMessagingService.Message>>) -> Unit,
    ): OcpMessageStreamReference {
        trace("Message Opening stream.")
        val streamReference = OcpMessageStreamReference(scope, "messaging")

        streamReference.retain()
        streamReference.timeoutHandler = {
            trace("Message Stream timed out")
            streamReference.coroutineScope.launch {
                openMessageStream(
                    scope = scope,
                    rendezvous = rendezvous,
                    streamRef = streamReference,
                    onEvent = onEvent
                )
            }
        }

        streamReference.coroutineScope.launch {
            openMessageStream(scope, rendezvous, streamReference, onEvent)
        }

        return streamReference
    }


    private suspend fun openMessageStream(
        scope: CoroutineScope,
        rendezvous: KeyPair,
        streamRef: OcpMessageStreamReference,
        onEvent: (Result<List<RpcMessagingService.Message>>) -> Unit
    ) {
        openBidirectionalStream(
            streamRef = streamRef,
            apiCall = api::openMessageStreamWithKeepAlive,
            initialRequest = {
                MessagingService.OpenMessageStreamWithKeepAliveRequest.newBuilder()
                    .setRequest(openMessageStreamRequest(rendezvous))
                    .build()
            },
            reconnectOnUnavailable = true,
            reconnectOnCancelled = true,
            reconnectHandler = {
                streamRef.coroutineScope.launch {
                    openMessageStream(scope, rendezvous, streamRef, onEvent)
                }
            },
            responseHandler = { response, onResult, requestChannel ->
                when (response.responseOrPingCase) {
                    MessagingService.OpenMessageStreamWithKeepAliveResponse.ResponseOrPingCase.RESPONSE -> {
                        onResult(Result.success(response.response.messagesList))
                    }

                    MessagingService.OpenMessageStreamWithKeepAliveResponse.ResponseOrPingCase.PING -> {
                        val request =
                            MessagingService.OpenMessageStreamWithKeepAliveRequest.newBuilder()
                                .setPong(clientPongWith(System.currentTimeMillis()))
                                .build()

                        streamRef.receivedPing(updatedTimeout = response.ping.pingDelay.seconds * 1_000L)
                        requestChannel(request)
                        trace(
                            message = "Pong Message Stream Server timestamp: ${response.ping.timestamp}",
                        )
                    }

                    MessagingService.OpenMessageStreamWithKeepAliveResponse.ResponseOrPingCase.RESPONSEORPING_NOT_SET -> {
                        trace(
                            message = "Message Stream Server sent empty message. This is unexpected.",
                            type = TraceType.Error,
                        )
                    }

                    else -> {
                        trace(
                            message = "Pong Message Stream sent null response. This is unexpected.",
                        )
                    }
                }
            }
        ).fold(
            onFailure = { onEvent(Result.failure(it)) },
            onSuccess = { onEvent(Result.success(it)) }
        )
    }

    suspend fun pollMessages(
        rendezvous: KeyPair,
    ): Result<List<MessagingService.Message>> {
        return runCatching { api.pollMessages(rendezvous) }
            .fold(
                onSuccess = { response ->
                    Result.success(response.messagesList)
                },
                onFailure = {
                    return Result.failure(PollMessagesError.Other(cause = it))
                }
            )
    }

    suspend fun ackMessages(
        rendezvous: KeyPair,
        messageIds: List<MessagingService.MessageId> = emptyList(),
    ): Result<Unit> {
        return runCatching { api.ackMessages(rendezvous, messageIds) }
            .fold(
                onSuccess = { response ->
                    when (response.result) {
                        RpcMessagingService.AckMesssagesResponse.Result.OK -> Result.success(Unit)
                        RpcMessagingService.AckMesssagesResponse.Result.UNRECOGNIZED -> {
                            Result.failure(AckMessagesError.Unrecognized())
                        }

                        else -> Result.failure(AckMessagesError.Other())
                    }
                },
                onFailure = { error ->
                    Result.failure(PollMessagesError.Other(cause = error))
                }
            )
    }

    suspend fun sendMessage(
        rendezvous: KeyPair,
        message: RpcMessagingService.Message.Builder,
    ): Result<PublicKey> {
        return runCatching { api.sendMessage(rendezvous = rendezvous, message = message) }
            .fold(
                onSuccess = { response ->
                    when (response.result) {
                        RpcMessagingService.SendMessageResponse.Result.OK -> {
                            Result.success(response.messageId.toPublicKey())
                        }

                        RpcMessagingService.SendMessageResponse.Result.UNRECOGNIZED -> {
                            Result.failure(SendMessageError.Unrecognized())
                        }

                        RpcMessagingService.SendMessageResponse.Result.NO_ACTIVE_STREAM -> {
                            Result.failure(SendMessageError.NoActiveStream())
                        }

                        else -> Result.failure(SendMessageError.Other())
                    }
                },
                onFailure = { error ->
                    Result.failure(SendMessageError.Other(cause = error))
                }
            )
    }
}