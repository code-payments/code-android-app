package com.getcode.opencode.internal.transactors

import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.controllers.MessagingController
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.internal.extensions.toPublicKey
import com.getcode.opencode.internal.network.extensions.asProtobufMessage
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.core.OpenCodePayload
import com.getcode.opencode.model.core.PayloadKind
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.transactions.TransactionMetadata
import com.getcode.opencode.utils.nonce
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.CodeServerError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel

internal class GiveBillTransactor(
    private val messagingController: MessagingController,
    private val transactionController: TransactionController,
    private val scope: CoroutineScope,
) {
    private var amount: LocalFiat? = null
    private var owner: AccountCluster? = null
    private var payload: OpenCodePayload? = null
    private var data: List<Byte>? = null

    private var rendezvousKey: KeyPair? = null
    private var receivingAccount: PublicKey? = null

    fun with(amount: LocalFiat, owner: AccountCluster): List<Byte> {
        this.amount = amount
        this.owner = owner

        receivingAccount = null
        return OpenCodePayload(
            kind = PayloadKind.Cash,
            value = amount.converted,
            nonce = nonce
        ).also {
            payload = it
            rendezvousKey = it.rendezvous
            data = it.codeData.toList()
        }.codeData.toList()
    }

    suspend fun start(): Result<TransactionMetadata.SendPublicPayment> {
        val ownerKey = owner
            ?: return Result.failure(GiveTransactorError.Other(message = "No owner key. Did you call with() first?"))
        val rendezvous = rendezvousKey
            ?: return Result.failure(GiveTransactorError.Other(message = "No rendezvous key. Did you call with() first?"))
        val transferRequest = messagingController.awaitRequestToGrabBill(scope, rendezvous)
            ?: return Result.failure(GiveTransactorError.Other(message = "No message received"))

        // 1. Validate that destination hasn't been tampered with by
        // verifying the signature matches one that has been signed
        // with the rendezvous key.
        val data = transferRequest.asProtobufMessage().toByteArray()
        val isValid = rendezvous.verify(transferRequest.signature.byteArray, data)

        if (!isValid) {
            return Result.failure(GiveTransactorError.DestinationSignatureInvalidException())
        }

        // 2. Send the funds to destination
        if (receivingAccount == transferRequest.account) {
            // Ensure that we're processing one, and only one
            // transaction for each instance of SendTransaction.
            // Completion will be called by the first invocation
            // of this function.
            return Result.failure(GiveTransactorError.DuplicateTransferException())
        }

        receivingAccount = transferRequest.account


        return transactionController.transfer(
            scope = scope,
            amount = amount!!,
            owner = ownerKey,
            destination = transferRequest.account,
            rendezvous = rendezvous.toPublicKey()
        ).fold(
            onSuccess = {
                transactionController.pollIntentMetadata(
                    owner = ownerKey.authority.keyPair,
                    intentId = it.id
                )
            }, onFailure = { Result.failure(it) })
    }

    fun dispose() {
        messagingController.cancelAwaitForBillGrab()
        scope.cancel()
    }

    sealed class GiveTransactorError(
        override val message: String? = null,
        override val cause: Throwable? = null
    ) : CodeServerError(message, cause) {
        class DuplicateTransferException : GiveTransactorError()
        class DestinationSignatureInvalidException : GiveTransactorError()
        data class Other(
            override val message: String? = null,
            override val cause: Throwable? = null
        ) : GiveTransactorError()
    }
}