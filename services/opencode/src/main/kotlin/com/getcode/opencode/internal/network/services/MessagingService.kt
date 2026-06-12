package com.getcode.opencode.internal.network.services

import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.bidi.BidirectionalStreamReference
import com.getcode.opencode.internal.bidi.openBidirectionalStreamForResult
import com.getcode.opencode.internal.network.api.MessagingApi
import com.getcode.opencode.internal.network.extensions.clientPongWith
import com.getcode.opencode.internal.network.extensions.foldWithSuppression
import com.getcode.opencode.internal.network.extensions.openMessageStreamRequest
import com.getcode.opencode.internal.network.extensions.toPublicKey
import com.getcode.opencode.model.core.errors.AckMessagesError
import com.getcode.opencode.model.core.errors.PollMessagesError
import com.getcode.opencode.model.core.errors.SendMessageError
import com.getcode.opencode.utils.toValidationOrElse
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.codeinc.opencode.gen.messaging.v1.OcpMessagingService

typealias OcpMessageStreamReference = BidirectionalStreamReference<OcpMessagingService.OpenMessageStreamWithKeepAliveRequest, OcpMessagingService.OpenMessageStreamWithKeepAliveResponse>

internal class MessagingService @Inject constructor(
    private val api: MessagingApi,
) {
    fun openMessageStreamWithKeepAlive(
        scope: CoroutineScope,
        rendezvous: KeyPair,
        messageFilter: (List<OcpMessagingService.Message>) -> Boolean = { true },
        onEvent: (Result<List<OcpMessagingService.Message>>) -> Unit,
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
                    messageFilter = messageFilter,
                    onEvent = onEvent
                )
            }
        }

        streamReference.coroutineScope.launch {
            openMessageStream(scope, rendezvous, streamReference, messageFilter, onEvent)
        }

        return streamReference
    }


    private suspend fun openMessageStream(
        scope: CoroutineScope,
        rendezvous: KeyPair,
        streamRef: OcpMessageStreamReference,
        messageFilter: (List<OcpMessagingService.Message>) -> Boolean,
        onEvent: (Result<List<OcpMessagingService.Message>>) -> Unit
    ) {
        openBidirectionalStreamForResult(
            streamRef = streamRef,
            apiCall = api::openMessageStreamWithKeepAlive,
            initialRequest = {
                OcpMessagingService.OpenMessageStreamWithKeepAliveRequest.newBuilder()
                    .setRequest(openMessageStreamRequest(rendezvous))
                    .build()
            },
            reconnectOnUnavailable = true,
            reconnectOnCancelled = true,
            reconnectHandler = {
                streamRef.coroutineScope.launch {
                    openMessageStream(scope, rendezvous, streamRef, messageFilter, onEvent)
                }
            },
            responseHandler = { response, onResult, requestChannel ->
                when (response.responseOrPingCase) {
                    OcpMessagingService.OpenMessageStreamWithKeepAliveResponse.ResponseOrPingCase.RESPONSE -> {
                        val messages = response.response.messagesList
                        if (messageFilter(messages)) {
                            onResult(Result.success(messages))
                        }
                    }

                    OcpMessagingService.OpenMessageStreamWithKeepAliveResponse.ResponseOrPingCase.PING -> {
                        val request =
                            OcpMessagingService.OpenMessageStreamWithKeepAliveRequest.newBuilder()
                                .setPong(clientPongWith(System.currentTimeMillis()))
                                .build()

                        streamRef.receivedPing(updatedTimeout = response.ping.pingDelay.seconds * 1_000L)
                        requestChannel(request)
                        trace(
                            message = "Pong Message Stream Server timestamp: ${response.ping.timestamp}",
                        )
                    }

                    OcpMessagingService.OpenMessageStreamWithKeepAliveResponse.ResponseOrPingCase.RESPONSEORPING_NOT_SET -> {
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
        ).foldWithSuppression(
            onFailure = { onEvent(Result.failure(it)) },
            onSuccess = { onEvent(Result.success(it)) }
        )
    }

    suspend fun pollMessages(
        rendezvous: KeyPair,
    ): Result<List<OcpMessagingService.Message>> {
        trace("Message polling.")
        return runCatching { api.pollMessages(rendezvous) }
            .foldWithSuppression(
                onSuccess = { response ->
                    Result.success(response.messagesList)
                },
                onFailure = {
                    Result.failure(PollMessagesError.Other(cause = it))
                }
            )
    }

    suspend fun ackMessages(
        rendezvous: KeyPair,
        messageIds: List<OcpMessagingService.MessageId> = emptyList(),
    ): Result<Unit> {
        return runCatching { api.ackMessages(rendezvous, messageIds) }
            .foldWithSuppression(
                onSuccess = { response ->
                    when (response.result) {
                        OcpMessagingService.AckMesssagesResponse.Result.OK -> Result.success(Unit)
                        OcpMessagingService.AckMesssagesResponse.Result.UNRECOGNIZED -> {
                            Result.failure(AckMessagesError.Unrecognized())
                        }

                        else -> Result.failure(AckMessagesError.Other())
                    }
                },
                onFailure = { error ->
                    Result.failure(error.toValidationOrElse { PollMessagesError.Other(cause = it) })
                }
            )
    }

    suspend fun sendMessage(
        rendezvous: KeyPair,
        message: OcpMessagingService.Message.Builder,
    ): Result<PublicKey> {
        return runCatching { api.sendMessage(rendezvous = rendezvous, message = message) }
            .foldWithSuppression(
                onSuccess = { response ->
                    when (response.result) {
                        OcpMessagingService.SendMessageResponse.Result.OK -> {
                            Result.success(response.messageId.toPublicKey())
                        }

                        OcpMessagingService.SendMessageResponse.Result.UNRECOGNIZED -> {
                            Result.failure(SendMessageError.Unrecognized())
                        }

                        OcpMessagingService.SendMessageResponse.Result.NO_ACTIVE_STREAM -> {
                            Result.failure(SendMessageError.NoActiveStream())
                        }

                        else -> Result.failure(SendMessageError.Other())
                    }
                },
                onFailure = { error ->
                    Result.failure(error.toValidationOrElse { SendMessageError.Other(cause = it) })
                }
            )
    }
}