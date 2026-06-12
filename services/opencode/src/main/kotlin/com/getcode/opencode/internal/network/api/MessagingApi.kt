package com.getcode.opencode.internal.network.api

import com.codeinc.opencode.gen.common.v1.Model
import com.codeinc.opencode.gen.messaging.v1.MessagingGrpcKt
import com.codeinc.opencode.gen.messaging.v1.OcpMessagingService
import com.codeinc.opencode.gen.messaging.v1.validate
import com.getcode.ed25519.Ed25519
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.annotations.OpenCodeManagedChannel
import com.getcode.opencode.internal.network.core.GrpcApi
import com.getcode.opencode.internal.network.extensions.asRendezvousKey
import com.getcode.opencode.internal.network.extensions.sign
import com.getcode.utils.trace
import com.google.protobuf.ByteString
import dev.bmcreations.protovalidate.orThrow
import io.grpc.ManagedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class MessagingApi @Inject constructor(
    @OpenCodeManagedChannel
    managedChannel: ManagedChannel,
) : GrpcApi(managedChannel) {

    private val api = MessagingGrpcKt.MessagingCoroutineStub(managedChannel)
        .withWaitForReady()

    /**
     * Opens a stream of messages. Messages are routed using the public key of a rendezvous keypair
     * derived by both the sender and the recipient of the messages. The sender may be a client or server.
     *
     * <p>
     *
     * Messages are expected to be acked once they have been processed by the client. Ack'd messages
     * will no longer be delivered on future OpenMessageStream calls, and are eligible for deletion
     * from the service. Clients should, however, handle duplicate delivery of messages.
     *
     * <p>
     *
     * For giving/grabbing a bill, the expected flow is as follows:
     * 1. The payment sender creates a multi-mint cash scan code.
     * 2. The payment sender calls OpenMessageStream on the rendezvous public key.
     * 3. The payment sender uses SendMessage to send the mint in a RequestToGiveBill message.
     * 4. The payment sender shows the bill with the scan code containing the rendezvous public key.
     * 5. The payment recipient scans the code.
     * 6. The payment recipient uses PollMessages to get the RequestToGiveBill message from part 3.
     * 7. The payment recipient sends the destination address in a RequestToGrabBill message.
     * 8. The payment sender receives the RequestToGrabBill message in real time, submits the intent
     *    for the payment to the provided destination, and then closes the stream.
     *
     */
    fun openMessageStream(
        rendezvous: KeyPair
    ): Flow<OcpMessagingService.OpenMessageStreamResponse> {
        val request = OcpMessagingService.OpenMessageStreamRequest.newBuilder()
            .setRendezvousKey(rendezvous.asRendezvousKey())
            .apply { setSignature(sign(rendezvous)) }
            .build()

        request.validate().orThrow()

        return api.openMessageStream(request)
    }

    /**
     * Opens a message stream with a ping/pong keepalive mechanism to monitor the health of the stream
     * at both client and server ends. This is an enhanced version of OpenMessageStream.
     *
     * <p>
     *
     * The keepalive protocol operates as follows:
     *
     * 1. Client initiates a stream by sending an OpenMessageStreamRequest.
     * 2. Upon stream initialization, server begins the keepalive protocol.
     * 3. Server sends a ping to the client.
     * 4. Client responds with a pong as quickly as possible, noting the delay to anticipate
     * the next ping.
     * 5. Steps 3 and 4 repeat until the stream is explicitly terminated or deemed unhealthy.
     *
     * <p>
     *
     * Client considerations:
     * - Client should process messages asynchronously to ensure ping responses are not delayed.
     * - Clients should implement a reasonable backoff strategy for repeated timeout failures.
     * - Clients abusing pong messages may have their streams terminated by the server.
     *
     * <p>
     *
     * The server will deliver messages in real-time as they are observed throughout the stream's
     * lifetime. Messages sent over the stream should not impact the timing of the ping/pong protocol.
     * Payment flow protocols remain identical to those documented in OpenMessageStream.
     *
     * <p>
     *
     * > NOTE: This API requires OpenMessageStreamRequest.signature to be set as part of the
     * migration to this updated protocol.
     *
     * @see openMessageStream
     */
    fun openMessageStreamWithKeepAlive(
        requestFlow: Flow<OcpMessagingService.OpenMessageStreamWithKeepAliveRequest>,
    ): Flow<OcpMessagingService.OpenMessageStreamWithKeepAliveResponse> {
        return api.openMessageStreamWithKeepAlive(requestFlow)
    }

    /**
     * Retrieves messages using a polling mechanism instead of a real-time stream like
     * OpenMessageStream. Message updates are not delivered in real-time and depend on the
     * polling interval. This RPC supports all message types.
     * <p>
     * <b>Note:</b> This is a temporary RPC implementation until OpenMessageStream can be
     * enhanced to support generic usage across both client and server, including features
     * such as multiple listeners.
     *
     * @see openMessageStream
     */
    suspend fun pollMessages(
        rendezvous: KeyPair
    ): OcpMessagingService.PollMessagesResponse {
        val channelState = managedChannels.first().getState(false)
        trace(tag = "gRPC", message = "pollMessages channel state: $channelState")

        val request = OcpMessagingService.PollMessagesRequest.newBuilder()
            .setRendezvousKey(rendezvous.asRendezvousKey())
            .apply { setSignature(sign(rendezvous)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) { api.pollMessages(request) }
    }

    /**
     * Acks one or more messages that have been successfully delivered to the client.
     */
    suspend fun ackMessages(
        rendezvous: KeyPair,
        messageIds: List<OcpMessagingService.MessageId> = emptyList()
    ): OcpMessagingService.AckMesssagesResponse {
        val request = OcpMessagingService.AckMessagesRequest.newBuilder()
            .setRendezvousKey(rendezvous.asRendezvousKey())
            .addAllMessageIds(messageIds)
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) { api.ackMessages(request) }
    }

    /**
     * Sends a message
     */
    suspend fun sendMessage(
        message: OcpMessagingService.Message.Builder,
        rendezvous: KeyPair,
    ): OcpMessagingService.SendMessageResponse {
        val signature = ByteArrayOutputStream().let {
            message.buildPartial().writeTo(it)
            val signed = Ed25519.sign(it.toByteArray(), rendezvous)
            Model.Signature.newBuilder().setValue(ByteString.copyFrom(signed))
        }

        val request = OcpMessagingService.SendMessageRequest.newBuilder()
            .setMessage(message)
            .setRendezvousKey(rendezvous.asRendezvousKey())
            .setSignature(signature)
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) { api.sendMessage(request) }
    }
}