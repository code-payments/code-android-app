package com.getcode.network.repository

import com.codeinc.gen.messaging.v1.MessagingService
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.model.IntentMetadata
import com.getcode.network.client.Client
import com.getcode.network.client.pollIntentMetadata
import com.getcode.solana.organizer.Organizer
import com.getcode.utils.ErrorUtils
import io.reactivex.rxjava3.core.Flowable
import kotlinx.coroutines.rx3.asFlowable
import javax.inject.Inject

class ReceiveTransactionRepository @Inject constructor(
    private val messagingRepository: MessagingRepository,
    private val client: Client
) {
    fun start(organizer: Organizer, rendezvous: KeyPair, debug: Boolean = false): Flowable<IntentMetadata> {
        return messagingRepository.sendRequestToGrabBill(
            destination = organizer.incomingVault.byteArray,
            rendezvousKeyPair = rendezvous
        )
            .flatMap { paymentRequestResponse ->
                if (paymentRequestResponse.result != MessagingService.SendMessageResponse.Result.OK) {
                    Flowable.error(Exception("Error: ${paymentRequestResponse.result.name}"))
                } else {
                    client.pollIntentMetadata(
                        owner = organizer.ownerKeyPair,
                        intentId = rendezvous.publicKeyBytes.toPublicKey(),
                        debugLogs = debug,
                    ).asFlowable()
                }
            }
            .doOnError {
                ErrorUtils.handleError(it)
            }
    }
}