package com.getcode.opencode.internal.transactors

import com.getcode.opencode.controllers.MessagingController
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.internal.extensions.filterIsInstance
import com.getcode.opencode.internal.extensions.toPublicKey
import com.getcode.opencode.internal.transactors.SendBillTransactor.SendTransactorError
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.core.OpenCodePayload
import com.getcode.opencode.model.transactions.TransactionMetadata
import com.getcode.solana.keys.base58
import com.getcode.utils.base58
import com.getcode.utils.getPublicKeyBase58
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel

internal class ReceiveBillTransactor(
    private val messagingController: MessagingController,
    private val transactionController: TransactionController,
    private val scope: CoroutineScope,
) {
    private var owner: AccountCluster? = null
    private var payload: OpenCodePayload? = null

    fun with(owner: AccountCluster, payload: OpenCodePayload) {
        this.owner = owner
        this.payload = payload
    }

    suspend fun start(): Result<TransactionMetadata.SendPublicPayment> {
        val ownerKey = owner ?: return Result.failure(SendTransactorError.Other(message = "No owner key. Did you call with() first?"))
        val destination = ownerKey.vaultPublicKey
        val data = payload
            ?: return Result.failure(SendTransactorError.Other(message = "No payload found. Did you call with() first?"))


        return messagingController.sendRequestToGrabBill(
            destination = destination,
            payload = data
        ).fold(
            onSuccess = {
                println("""
                    polling for metadata:
                    rendezvous: ${data.rendezvous.toPublicKey().base58()} (${data.rendezvous.publicKeyBytes.size})
                """.trimIndent())
                transactionController.pollIntentMetadata(
                    owner = ownerKey.authority.keyPair,
                    intentId = data.rendezvous.toPublicKey()
                )
            }, onFailure = { Result.failure(it) })
    }

    fun dispose() {
        scope.cancel()
    }
}