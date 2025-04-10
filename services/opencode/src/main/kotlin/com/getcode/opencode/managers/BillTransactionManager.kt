package com.getcode.opencode.managers

import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.controllers.MessagingController
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.internal.extensions.toPublicKey
import com.getcode.opencode.internal.model.account.AccountCluster
import com.getcode.opencode.internal.network.extensions.asProtobufMessage
import com.getcode.opencode.model.core.LocalFiat
import com.getcode.opencode.model.core.OpenCodePayload
import com.getcode.opencode.model.core.PayloadKind
import com.getcode.opencode.model.messaging.MessageKind
import com.getcode.opencode.model.transactions.TransactionMetadata
import com.getcode.opencode.utils.nonce
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.CodeServerError
import com.getcode.utils.ErrorUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.Timer
import java.util.TimerTask
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.schedule

@Singleton
class BillTransactionManager @Inject constructor(
    private val messagingController: MessagingController,
    private val transactionController: TransactionController,
) {
    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var billDismissTimer: TimerTask? = null
    private var transactor: Transactor? = null

    fun awaitGrabFromRecipient(
        amount: LocalFiat,
        owner: AccountCluster,
        present: (List<Byte>) -> Unit,
        onGrabbed: () -> Unit,
        onTimeout: () -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        transactor?.dispose()
        val payload = Transactor(messagingController, transactionController, scope).also {
            transactor = it
        }.with(amount, owner)

        scope.launch {
            transactor?.start()
                ?.onSuccess { onGrabbed() }
                ?.onFailure {
                    ErrorUtils.handleError(it)
                    onError(it)
                }
        }

        presentSend(onTimeout)
        present(payload)

    }

    private fun presentSend(onTimeout: () -> Unit) {
        cancelBillTimeout()
        billDismissTimer = Timer().schedule((1000 * 50).toLong()) {
            onTimeout()
        }
    }

    fun cancelSend() {
        cancelBillTimeout()
    }

    fun cancelBillTimeout() {
        billDismissTimer?.cancel()
    }
}

private class Transactor(
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

    suspend fun start(): Result<TransactionMetadata> {
        val ownerKey = owner
            ?: return Result.failure(TransactorError.Other(message = "No owner key. Did you call with() first?"))
        val rendezvous = rendezvousKey
            ?: return Result.failure(TransactorError.Other(message = "No rendezvous key. Did you call with() first?"))
        val message = messagingController.openMessageStream(rendezvous)
            .firstOrNull()?.firstOrNull()

        // 1. Validate that destination hasn't been tampered with by
        // verifying the signature matches one that has been signed
        // with the rendezvous key.
        val isValid = if (message != null) {
            val data = message.asProtobufMessage().requestToGrabBill.toByteArray()
            rendezvous.verify(message.signature.byteArray, data)
        } else {
            false
        }

        if (!isValid) {
            return Result.failure(TransactorError.DestinationSignatureInvalidException())
        }

        val paymentRequest = message!!.kind as MessageKind.RequestToGrabBill

        // 2. Send the funds to destination
        if (receivingAccount == paymentRequest.requestor) {
            // Ensure that we're processing one, and only one
            // transaction for each instance of SendTransaction.
            // Completion will be called by the first invocation
            // of this function.
            return Result.failure(TransactorError.DuplicateTransferException())
        }

        receivingAccount = paymentRequest.requestor


        return transactionController.transfer(
            scope = scope,
            amount = amount!!, owner = ownerKey,
            destination = paymentRequest.requestor
        ).fold(
            onSuccess = {
                transactionController.pollIntentMetadata(
                    owner = ownerKey.authority.keyPair,
                    intentId = rendezvous.toPublicKey()
                )
            }, onFailure = { Result.failure(it) })
    }

    fun dispose() {

    }

    sealed class TransactorError(
        override val message: String? = null,
        override val cause: Throwable? = null
    ) : CodeServerError(message, cause) {
        class DuplicateTransferException : TransactorError()
        class DestinationSignatureInvalidException : TransactorError()
        data class Other(
            override val message: String? = null,
            override val cause: Throwable? = null
        ) : TransactorError()
    }
}