package com.getcode.opencode.internal.transactors

import com.getcode.opencode.controllers.AccountController
import com.getcode.opencode.controllers.MessagingController
import com.getcode.opencode.controllers.TokenController
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.internal.extensions.toPublicKey
import com.getcode.opencode.internal.transactors.GiveBillTransactor.GiveTransactorError
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.core.OpenCodePayload
import com.getcode.opencode.model.transactions.TransactionMetadata
import com.getcode.utils.CodeServerError
import com.getcode.utils.ErrorUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel

internal class GrabBillTransactor(
    private val accountController: AccountController,
    private val messagingController: MessagingController,
    private val transactionController: TransactionController,
    private val tokenController: TokenController,
    private val scope: CoroutineScope,
) : Transactor<GrabTransactorError>("Transactor::Grab") {
    private var owner: AccountCluster? = null
    private var payload: OpenCodePayload? = null

    fun with(owner: AccountCluster, payload: OpenCodePayload) {
        this.owner = owner
        this.payload = payload
    }

    suspend fun start(): Result<TransactionMetadata.PublicPayment> {
        val ownerKey = owner
            ?: return logAndFail(GrabTransactorError.Other(message = "No owner cluster available. Did you call with() first?"))
        val data = payload
            ?: return logAndFail(GrabTransactorError.Other(message = "No payload available. Did you call with() first?"))

        // 1. Wait for the give request from the sender so we can determine what mint we are operating on
        val (messageId, giveRequestMint) = messagingController.pollForGiveRequest(data.rendezvous)
            .getOrNull()
            ?: return logAndFail(GiveTransactorError.Other(message = "No give request found for rendezvous"))

        // 2. Utilize the mint from the give request to get the Token metadata
        val token = tokenController.getTokenMetadata(giveRequestMint)
            .getOrNull()
            ?: return logAndFail(GrabTransactorError.Other(message = "No token found for proposed mint"))

        val tokenizedCluster = ownerKey.withTimelockForToken(token)

        // 3. create an account if we don't currently have one for this token
        val userAccountResult = if (!accountController.hasAccountFor(token.address)) {
            accountController.createUserAccount(
                ownerForMint = tokenizedCluster,
                mint = token.address
            )
        } else {
            Result.success(Unit)
        }

        // 4. Send the grab request to the recipient with the correct vault
        return userAccountResult.map {
                messagingController.sendRequestToGrabBill(
                    destination = tokenizedCluster.vaultPublicKey,
                    payload = data
                )
            }.fold(
                onSuccess = {
                    // 5. Wait for confirmation
                    transactionController.pollIntentMetadata(
                        owner = tokenizedCluster.authority.keyPair,
                        intentId = data.rendezvous.toPublicKey(),
                        debugLogs = true
                    )
                }, onFailure = {
                    if (it !is GrabTransactorError) {
                        ErrorUtils.handleError(it)
                    }
                    logAndFail(it)
                }
            )
    }

    fun dispose() {
        owner = null
        payload = null

        scope.cancel()
    }
}

sealed class GrabTransactorError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : CodeServerError(message, cause) {
    data class Other(
        override val message: String? = null,
        override val cause: Throwable? = null
    ) : GrabTransactorError(message, cause)
}