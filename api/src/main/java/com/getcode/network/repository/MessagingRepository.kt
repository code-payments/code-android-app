package com.getcode.network.repository

import com.codeinc.gen.common.v1.Model
import com.codeinc.gen.messaging.v1.MessagingService
import com.codeinc.gen.messaging.v1.MessagingService.CodeScanned
import com.codeinc.gen.messaging.v1.MessagingService.MessageId
import com.codeinc.gen.messaging.v1.MessagingService.PollMessagesRequest
import com.codeinc.gen.messaging.v1.MessagingService.RendezvousKey
import com.google.protobuf.ByteString
import com.getcode.ed25519.Ed25519
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.solana.keys.Signature
import com.getcode.model.PaymentRequest
import com.getcode.model.StreamMessage
import com.getcode.solana.keys.PublicKey
import com.getcode.network.core.NetworkOracle
import com.getcode.network.api.MessagingApi
import com.getcode.network.core.INFINITE_STREAM_TIMEOUT
import com.getcode.utils.ErrorUtils
import com.google.protobuf.Timestamp
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.reactive.asFlow
import timber.log.Timber
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlin.math.sign

private const val TAG = "MessagingRepository"

class MessagingRepository @Inject constructor(
    private val messagingApi: MessagingApi,
    private val networkOracle: NetworkOracle,
) {

    fun openMessageStream(
        rendezvousKeyPair: KeyPair,
    ): Flowable<PaymentRequest> {
        Timber.i("openMessageStream")

        val request = MessagingService.OpenMessageStreamRequest.newBuilder()
            .setRendezvousKey(
                RendezvousKey.newBuilder().setValue(
                    ByteString.copyFrom(rendezvousKeyPair.publicKeyBytes)
                )
            )
            .build()

        return messagingApi.openMessageStream(request)
            .let { networkOracle.managedRequest(it, INFINITE_STREAM_TIMEOUT) }
            .map {
                it.messagesList
                    .filter { message ->
                        message.kindCase == MessagingService.Message.KindCase.REQUEST_TO_GRAB_BILL
                    }
            }
            .doOnNext { messagesList ->
                if (messagesList.isEmpty()) return@doOnNext
                ackMessages(rendezvousKeyPair, messagesList.map { it.id })
                    .subscribe({}, ErrorUtils::handleError)
            }
            .filter { it.isNotEmpty() }
            .map { messagesList ->
                messagesList.map { message ->
                    val account =
                        message.requestToGrabBill.requestorAccount.value.toByteArray().toPublicKey()
                    val signature =
                        Signature(message.sendMessageRequestSignature.value.toByteArray().toList())
                    PaymentRequest(account, signature)
                }.first()
            }
            .retry(10L)
            .subscribeOn(Schedulers.computation())
        //.subscribe({}, { error ->
        //})
        //.disposeBy(lifecycle)
    }

    private fun ackMessages(
        rendezvousKeyPair: KeyPair,
        messageIds: List<MessagingService.MessageId>
    ): Completable {
        val request = MessagingService.AckMessagesRequest.newBuilder()
            .addAllMessageIds(messageIds)
            .setRendezvousKey(
                RendezvousKey.newBuilder().setValue(
                    ByteString.copyFrom(rendezvousKeyPair.publicKeyBytes)
                )
            )
            .build()

        return networkOracle.managedRequest(messagingApi.ackMessages(request))
            .flatMapCompletable {
                if (it.result == MessagingService.AckMesssagesResponse.Result.OK) {
                    Timber.i("ackMessages: Result.OK: $messageIds")
                    Completable.complete()
                } else {
                    Completable.error(RuntimeException("Failed to ack message with ids: $messageIds"))
                }
            }
    }

    fun verifyRequestForPayment(
        destination: PublicKey,
        rendezvousKey: KeyPair,
        signature: Signature
    ): Boolean {
        val messageData = requestForPayment(destination = destination).build().toByteArray()
        return rendezvousKey.verify(signature.byteArray, messageData)
    }

    fun sendRequestForPayment(
        destination: ByteArray,
        rendezvousKeyPair: KeyPair,
    ): Flowable<MessagingService.SendMessageResponse> {
        val requestor = destination.toSolanaAccount()
        val paymentRequest = MessagingService.RequestToGrabBill.newBuilder()
            .setRequestorAccount(requestor)
        val message = MessagingService.Message.newBuilder()
            .setRequestToGrabBill(paymentRequest)

        return sendRendezvousMessageFlowable(message, rendezvousKeyPair)
            .doOnEach {
                Timber.i("sendRequestForPayment: result: ${it.value?.result}")
            }
    }

    fun fetchMessages(rendezvous: KeyPair): Result<List<StreamMessage>> {
        val request = PollMessagesRequest.newBuilder()
            .setRendezvousKey(
                RendezvousKey.newBuilder()
                    .setValue(ByteString.copyFrom(rendezvous.publicKeyBytes))
            ).let {
                val bos = ByteArrayOutputStream()
                it.buildPartial().writeTo(bos)
                it.setSignature(Ed25519.sign(bos.toByteArray(), rendezvous).toSignature())
            }
            .build()

        return networkOracle.managedRequest(messagingApi.pollMessages(request))
            .map { response ->
                Timber.d("response=${response.messagesList}")
                response.messagesList.mapNotNull { m -> StreamMessage.getInstance(m) }
            }.firstOrError().blockingGet().runCatching { this }
    }

    suspend fun codeScanned(rendezvous: KeyPair): Result<MessagingService.SendMessageResponse> {
        val message = MessagingService.Message.newBuilder()
            .setCodeScanned(CodeScanned.newBuilder()
                .setTimestamp(Timestamp.newBuilder().setSeconds(System.currentTimeMillis() / 1_000)))

        return sendRendezvousMessage(message, rendezvous)
    }

    suspend fun rejectPayment(rendezvous: KeyPair): Result<MessagingService.SendMessageResponse> {
        val rejection = MessagingService.ClientRejectedPayment.newBuilder()
            .setIntentId(PublicKey.fromBase58(rendezvous.getPublicKeyBase58()).toIntentId())
            .build()

        val message = MessagingService.Message.newBuilder()
            .setClientRejectedPayment(rejection)

        return sendRendezvousMessage(message, rendezvous)
    }

    private fun requestForPayment(destination: PublicKey): MessagingService.Message.Builder {
        return MessagingService.Message
            .newBuilder()
            .setRequestToGrabBill(
                MessagingService.RequestToGrabBill
                    .newBuilder()
                    .setRequestorAccount(destination.bytes.toSolanaAccount())
            )
    }

    private suspend fun sendRendezvousMessage(
        message: MessagingService.Message.Builder,
        rendezvous: KeyPair
    ): Result<MessagingService.SendMessageResponse> {
        val signature = ByteArrayOutputStream().let {
            message.buildPartial().writeTo(it)
            val signed = Ed25519.sign(it.toByteArray(), rendezvous)
            Model.Signature.newBuilder().setValue(ByteString.copyFrom(signed))
        }

        val request = MessagingService.SendMessageRequest.newBuilder()
            .setMessage(message)
            .setRendezvousKey(
                RendezvousKey.newBuilder().setValue(
                    ByteString.copyFrom(rendezvous.publicKeyBytes)
                )
            )
            .setSignature(signature)
            .build()

        return runCatching {
            messagingApi.sendMessage(request)
                .let { networkOracle.managedRequest(it) }
                .asFlow()
                .firstOrNull() ?: throw IllegalArgumentException()
        }.onSuccess {
            Timber.i("message sent: ${it.messageId.value.toList().hexEncodedString()}: result: ${it.result}")
        }
    }

    private fun sendRendezvousMessageFlowable(
        message: MessagingService.Message.Builder,
        rendezvous: KeyPair
    ): Flowable<MessagingService.SendMessageResponse> {
        val signature = ByteArrayOutputStream().let {
            message.buildPartial().writeTo(it)
            val signed = Ed25519.sign(it.toByteArray(), rendezvous)
            Model.Signature.newBuilder().setValue(ByteString.copyFrom(signed))
        }

        val request = MessagingService.SendMessageRequest.newBuilder()
            .setMessage(message)
            .setRendezvousKey(
                RendezvousKey.newBuilder().setValue(
                    ByteString.copyFrom(rendezvous.publicKeyBytes)
                )
            )
            .setSignature(signature)
            .build()

        return messagingApi.sendMessage(request)
            .let { networkOracle.managedRequest(it) }
    }
}