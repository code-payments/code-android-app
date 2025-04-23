package com.getcode.opencode.internal.transactors

import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.internal.extensions.toPublicKey
import com.getcode.opencode.internal.network.api.intents.IntentRemoteSend
import com.getcode.opencode.internal.transactors.GiveBillTransactor.GiveTransactorError
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.GiftCardAccount
import com.getcode.opencode.model.core.OpenCodePayload
import com.getcode.opencode.model.core.PayloadKind
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.utils.nonce
import com.getcode.utils.CodeServerError
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel

internal class SendBillTransactor(
    private val transactionController: TransactionController,
    private val scope: CoroutineScope,
) {
    private var amount: LocalFiat? = null
    private var owner: AccountCluster? = null
    private var payload: OpenCodePayload? = null
    private var data: List<Byte>? = null

    private var rendezvousKey: KeyPair? = null

    fun with(amount: LocalFiat, owner: AccountCluster): List<Byte> {
        this.amount = amount
        this.owner = owner

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

    suspend fun start(): Result<GiftCardAccount> {
        val ownerKey = owner
            ?: return Result.failure(GiveTransactorError.Other(message = "No owner key. Did you call with() first?"))
        val rendezvous = rendezvousKey
            ?: return Result.failure(GiveTransactorError.Other(message = "No rendezvous key. Did you call with() first?"))


        return transactionController.remoteSend(
            rendezvous = rendezvous.toPublicKey(),
            owner = ownerKey,
            amount = amount!!
        ).map { it as IntentRemoteSend }
            .map { it.giftCardAccount }
            .onFailure {
                trace(
                    tag = "SendTrx",
                    message = "Failed to create a gift card account",
                    error = it
                )
            }
    }

    fun dispose() {
        scope.cancel()
    }

    sealed class SendTransactorError(
        override val message: String? = null,
        override val cause: Throwable? = null
    ) : CodeServerError(message, cause) {
        data class Other(
            override val message: String? = null,
            override val cause: Throwable? = null
        ) : SendTransactorError()
    }
}