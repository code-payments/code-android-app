package com.getcode.network.repository

import com.codeinc.gen.common.v1.Model
import com.codeinc.gen.messaging.v1.MessagingService
import com.codeinc.gen.messaging.v1.MessagingService.ClientRejectedLogin
import com.codeinc.gen.messaging.v1.MessagingService.CodeScanned
import com.codeinc.gen.messaging.v1.MessagingService.PollMessagesRequest
import com.codeinc.gen.messaging.v1.MessagingService.RendezvousKey
import com.codeinc.gen.transaction.v2.TransactionService
import com.getcode.ed25519.Ed25519
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.model.Domain
import com.getcode.model.Fiat
import com.getcode.model.PaymentRequest
import com.getcode.model.StreamMessage
import com.getcode.model.toPublicKey
import com.getcode.network.api.MessagingApi
import com.getcode.services.network.core.INFINITE_STREAM_TIMEOUT
import com.getcode.services.network.core.NetworkOracle
import com.getcode.utils.ErrorUtils
import com.getcode.utils.getPublicKeyBase58
import com.getcode.utils.hexEncodedString
import com.google.protobuf.ByteString
import com.google.protobuf.Timestamp
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.reactive.asFlow
import timber.log.Timber
import java.io.ByteArrayOutputStream
import javax.inject.Inject

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
                Timber.d("message stream response received")
                it.messagesList
                    .filter { message ->
                        message.kindCase == MessagingService.Message.KindCase.REQUEST_TO_GRAB_BILL
                    }
            }
            .doOnNext { messagesList ->
                if (messagesList.isEmpty()) {
                    return@doOnNext
                }
                ackMessages(rendezvousKeyPair, messagesList.map { it.id })
                    .subscribe({ Timber.d("acked") }, ErrorUtils::handleError)
            }
            .filter { it.isNotEmpty() }
            .map { messagesList ->
                messagesList.map { message ->
                    val account =
                        message.requestToGrabBill.requestorAccount.value.toByteArray().toPublicKey()
                    val signature =
                        com.getcode.solana.keys.Signature(
                            message.sendMessageRequestSignature.value.toByteArray().toList()
                        )
                    PaymentRequest(account, signature)
                }.first()
            }
            .retry(10L) {
                it.printStackTrace()
                true
            }
            .subscribeOn(Schedulers.computation())
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

    fun verifyRequestToGrabBill(
        destination: com.getcode.solana.keys.PublicKey,
        rendezvousKey: KeyPair,
        signature: com.getcode.solana.keys.Signature
    ): Boolean {
        val messageData = sendRequestToGrabBill(destination = destination).build().toByteArray()
        return rendezvousKey.verify(signature.byteArray, messageData)
    }

    suspend fun sendRequestToLogin(
        domain: Domain,
        verifier: KeyPair,
        rendezvous: KeyPair,
    ): Result<MessagingService.SendMessageResponse> {
        val message = requestToLogin(domain, verifier, rendezvous)
        return sendRendezvousMessage(message, rendezvous)
    }

    fun sendRequestToGrabBill(
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

    suspend fun sendRequestToReceiveBill(
        destination: com.getcode.solana.keys.PublicKey,
        fiat: Fiat,
        rendezvous: KeyPair
    ): Result<MessagingService.SendMessageResponse> {
        val message = MessagingService.Message.newBuilder()
            .setRequestToReceiveBill(
                MessagingService.RequestToReceiveBill.newBuilder()
                    .setRequestorAccount(destination.byteArray.toSolanaAccount())
                    .setPartial(
                        TransactionService.ExchangeDataWithoutRate.newBuilder()
                            .setCurrency(fiat.currency.name)
                            .setNativeAmount(fiat.amount)
                    )
                    .build()
            )

        return sendRendezvousMessage(message, rendezvous)
    }

    fun fetchMessages(rendezvous: KeyPair): Result<List<StreamMessage>> {
        val request = PollMessagesRequest.newBuilder()
            .setRendezvousKey(
                RendezvousKey.newBuilder()
                    .setValue(ByteString.copyFrom(rendezvous.publicKeyBytes))
            ).apply { setSignature(sign(rendezvous)) }
            .build()

        return networkOracle.managedRequest(messagingApi.pollMessages(request))
            .observeOn(Schedulers.io())
            .map { response ->
                Timber.d("response=${response.messagesList}")
                response.messagesList.mapNotNull { m -> StreamMessage.getInstance(m) }
            }.firstOrError().blockingGet().runCatching { this }
    }

    suspend fun codeScanned(rendezvous: KeyPair): Result<MessagingService.SendMessageResponse> {
        val message = MessagingService.Message.newBuilder()
            .setCodeScanned(
                CodeScanned.newBuilder()
                    .setTimestamp(
                        Timestamp.newBuilder().setSeconds(System.currentTimeMillis() / 1_000)
                    )
            )

        return sendRendezvousMessage(message, rendezvous)
    }

    suspend fun rejectPayment(rendezvous: KeyPair): Result<MessagingService.SendMessageResponse> {
        val rejection = MessagingService.ClientRejectedPayment.newBuilder()
            .setIntentId(com.getcode.solana.keys.PublicKey.fromBase58(rendezvous.getPublicKeyBase58()).toIntentId())
            .build()

        val message = MessagingService.Message.newBuilder()
            .setClientRejectedPayment(rejection)

        return sendRendezvousMessage(message, rendezvous)
    }

    suspend fun rejectLogin(rendezvous: KeyPair): Result<MessagingService.SendMessageResponse> {
        val message = MessagingService.Message
            .newBuilder()
            .setClientRejectedLogin(
                ClientRejectedLogin.newBuilder()
                    .setTimestamp(
                        Timestamp.newBuilder().setSeconds(System.currentTimeMillis() / 1_000)
                    )
            )

        return sendRendezvousMessage(message, rendezvous)
    }

    private fun sendRequestToGrabBill(destination: com.getcode.solana.keys.PublicKey): MessagingService.Message.Builder {
        return MessagingService.Message
            .newBuilder()
            .setRequestToGrabBill(
                MessagingService.RequestToGrabBill
                    .newBuilder()
                    .setRequestorAccount(destination.bytes.toSolanaAccount())
            )
    }

    private fun requestToLogin(
        domain: Domain,
        verifier: KeyPair,
        rendezvous: KeyPair
    ): MessagingService.Message.Builder {
        return MessagingService.Message
            .newBuilder()
            .setRequestToLogin(
                MessagingService.RequestToLogin
                    .newBuilder()
                    .setDomain(
                        Model.Domain.newBuilder()
                            .setValue(domain.relationshipHost)
                    )
                    .setRendezvousKey(
                        RendezvousKey.newBuilder()
                            .setValue(ByteString.copyFrom(rendezvous.publicKeyBytes))
                    ).setVerifier(verifier.publicKeyBytes.toSolanaAccount())
                    .let {
                        val bos = ByteArrayOutputStream()
                        it.buildPartial().writeTo(bos)
                        it.setSignature(Ed25519.sign(bos.toByteArray(), rendezvous).toSignature())
                    }
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
            Timber.i(
                "message sent: ${
                    it.messageId.value.toList().hexEncodedString()
                }: result: ${it.result}"
            )
        }.onFailure {
            ErrorUtils.handleError(it)
            Timber.e(t = it, message = "Failed to send rendezvous message.")
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