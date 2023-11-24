package com.getcode.network.repository

import com.codeinc.gen.messaging.v1.MessagingService
import com.getcode.crypt.Sha256Hash
import com.getcode.ed25519.Ed25519
import com.getcode.model.IntentMetadata
import com.getcode.network.client.Client
import com.getcode.network.client.pollIntentMetadata
import com.getcode.solana.organizer.Organizer
import com.getcode.utils.ErrorUtils
import io.reactivex.rxjava3.core.Flowable
import javax.inject.Inject

class ReceiveTransactionRepository @Inject constructor(
    private val messagingRepository: MessagingRepository,
    private val client: Client
) {
    fun start(organizer: Organizer, payload: List<Byte>): Flowable<IntentMetadata> {
        val hash = Sha256Hash.hash(payload.toByteArray())
        val rendezvousKeyPair = Ed25519.createKeyPair(hash.encodeBase64())

        return messagingRepository.sendRequestForPayment(
            destination = organizer.incomingVault.byteArray,
            rendezvousKeyPair = rendezvousKeyPair
        )
            .flatMap { paymentRequestResponse ->
                if (paymentRequestResponse.result != MessagingService.SendMessageResponse.Result.OK) {
                    Flowable.error(Exception("Error: ${paymentRequestResponse.result.name}"))
                } else {
                    client.pollIntentMetadata(
                        owner = organizer.ownerKeyPair,
                        intentId = rendezvousKeyPair.publicKeyBytes.toPublicKey()
                    )
                }
            }
            .doOnError {
                ErrorUtils.handleError(it)
            }
    }
}