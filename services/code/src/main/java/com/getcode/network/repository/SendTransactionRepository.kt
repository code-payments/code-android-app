package com.getcode.network.repository

import com.getcode.analytics.AnalyticsService
import com.getcode.ed25519.Ed25519
import com.getcode.services.model.CodePayload
import com.getcode.model.IntentMetadata
import com.getcode.model.Kin
import com.getcode.model.KinAmount
import com.getcode.services.model.Kind
import com.getcode.model.toPublicKey
import com.getcode.network.client.Client
import com.getcode.network.client.pollIntentMetadata
import com.getcode.network.client.transfer
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.organizer.Organizer
import com.getcode.services.utils.nonce
import io.reactivex.rxjava3.core.Flowable
import kotlinx.coroutines.rx3.asFlowable
import javax.inject.Inject


class SendTransactionRepository @Inject constructor(
    private val messagingRepository: MessagingRepository,
    private val analyticsManager: AnalyticsService,
    private val client: Client,
) {
    private lateinit var amount: KinAmount
    private lateinit var organizer: Organizer
    private lateinit var owner: Ed25519.KeyPair
    private lateinit var payload: CodePayload
    private lateinit var payloadData: List<Byte>

    private lateinit var rendezvousKey: Ed25519.KeyPair
    private var receivingAccount: PublicKey? = null

    fun init(amount: KinAmount, organizer: Organizer, owner: Ed25519.KeyPair): List<Byte> {
        this.amount = amount
        this.organizer = organizer
        this.owner = owner

        this.payload = CodePayload(
            kind = Kind.Cash,
            value = amount.kin,
            nonce = nonce,
        )

        this.payloadData = payload.codeData.toList()
        this.rendezvousKey = payload.rendezvous
        this.receivingAccount = null

        return payloadData
    }

    fun startTransaction(): Flowable<IntentMetadata> {
        return messagingRepository.openMessageStream(rendezvousKey)
            .firstOrError()
            .flatMapPublisher { paymentRequest ->
                // 1. Validate that destination hasn't been tampered with by
                // verifying the signature matches one that has been signed
                // with the rendezvous key.

                val isValid = messagingRepository.verifyRequestToGrabBill(
                    destination = paymentRequest.account,
                    rendezvousKey = rendezvousKey,
                    signature = paymentRequest.signature
                )

                if (!isValid) {
                    analyticsManager.transfer(
                        amount = amount,
                        successful = false
                    )

                    Flowable.error(SendTransactionException.DestinationSignatureInvalidException())
                } else {
                    analyticsManager.transfer(
                        amount = amount,
                        successful = true
                    )

                    // 2. Send the funds to destination
                    sendFundsAndPoll(organizer, paymentRequest.account)
                }
            }
            .doOnError {
                analyticsManager.transfer(
                    amount = amount,
                    successful = false
                )
            }
    }

    private fun sendFundsAndPoll(
        organizer: Organizer,
        destination: PublicKey
    ): Flowable<IntentMetadata> {
        if (receivingAccount == destination) {
            // Ensure that we're processing one, and only one
            // transaction for each instance of SendTransaction.
            // Completion will be called by the first invocation
            // of this function.
            return Flowable.error(SendTransactionException.DuplicateTransferException())
        }

        receivingAccount = destination

        // It's possible that we have funds sitting in the incoming
        // account counting towards the active balance. If we don't
        // deposit the funds and the transaction size exceeds what's
        // in the buckets, the send will fail.
        return client.transfer(
            amount = amount.copy(kin = amount.kin.toKinTruncating()),
            fee = Kin.fromKin(0),
            additionalFees = emptyList(),
            organizer = organizer,
            rendezvousKey = rendezvousKey.publicKeyBytes.toPublicKey(),
            destination = destination,
            isWithdrawal = false
        )
            .andThen(
                client.pollIntentMetadata(
                    owner = organizer.ownerKeyPair,
                    intentId = rendezvousKey.publicKeyBytes.toPublicKey()
                ).asFlowable()
            )
    }

    fun getAmount() = amount
    fun getRendezvous() = rendezvousKey

    sealed class SendTransactionException : Exception() {
        class DuplicateTransferException : SendTransactionException()
        class DestinationSignatureInvalidException : SendTransactionException()
    }
}