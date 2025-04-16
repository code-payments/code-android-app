package com.getcode.opencode.controllers

import com.codeinc.opencode.gen.messaging.v1.MessagingService
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.network.extensions.asSolanaAccountId
import com.getcode.opencode.internal.network.services.OcpMessageStreamReference
import com.getcode.opencode.model.core.OpenCodePayload
import com.getcode.opencode.model.transactions.TransferRequest
import com.getcode.opencode.repositories.MessagingRepository
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class MessagingController @Inject constructor(
    private val repository: MessagingRepository,
) {
    private var streamReference: OcpMessageStreamReference? = null

    suspend fun awaitRequestToGrabBill(
        scope: CoroutineScope,
        rendezvous: KeyPair,
    ): TransferRequest? = suspendCancellableCoroutine { cont ->
        cancelAwaitForBillGrab()
        streamReference = repository.openMessageStreamWithKeepAlive(scope, rendezvous) { result ->
            result.onSuccess {
                cont.resume(it)
            }.onFailure {
                trace(
                    tag = "Messaging",
                    message = it.message.orEmpty(),
                    type = TraceType.Silent
                )
            }
        }
    }

    fun cancelAwaitForBillGrab() {
        streamReference?.destroy()
        streamReference = null
    }

    suspend fun sendRequestToGrabBill(
        destination: PublicKey,
        payload: OpenCodePayload,
    ): Result<PublicKey> {
        val paymentRequest = MessagingService.RequestToGrabBill.newBuilder()
            .setRequestorAccount(destination.asSolanaAccountId())

        val message = MessagingService.Message.newBuilder()
            .setRequestToGrabBill(paymentRequest)

        return repository.sendMessage(
            message = message,
            rendezvous = payload.rendezvous
        )
    }
}