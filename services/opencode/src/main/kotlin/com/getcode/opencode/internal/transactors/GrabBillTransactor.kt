package com.getcode.opencode.internal.transactors

import com.getcode.opencode.controllers.AccountController
import com.getcode.opencode.controllers.MessagingController
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.internal.extensions.toPublicKey
import com.getcode.opencode.internal.transactors.GiveBillTransactor.GiveTransactorError
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.core.OpenCodePayload
import com.getcode.opencode.model.core.PayloadKind
import com.getcode.opencode.model.transactions.TransactionMetadata
import com.getcode.opencode.providers.TokenMetadataProvider
import com.getcode.utils.CodeServerError
import com.getcode.utils.ErrorUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel

internal class GrabBillTransactor(
    private val accountController: AccountController,
    private val messagingController: MessagingController,
    private val transactionController: TransactionController,
    private val tokenProvider: TokenMetadataProvider,
    private val scope: CoroutineScope,
) : Transactor<GrabTransactorError>("Transactor::Grab") {
    private var owner: AccountCluster? = null
    private var payload: OpenCodePayload? = null

    fun with(owner: AccountCluster, payload: OpenCodePayload) {
        this.owner = owner
        this.payload = payload
    }

    /**
     * Claims a cash bill from a sender, handling both legacy single-mint and
     * multi-mint payment flows.
     *
     * Flow (dispatched by [PayloadKind]):
     *
     *  **Legacy Cash** — sends a grab request directly, then polls for the
     *  intent confirmation.
     *
     *  **MultiMintCash:**
     *   1. Poll the rendezvous messaging stream for the sender's "give" request
     *      to learn which token mint and exchange data to use.
     *   2. Resolve token metadata for the proposed mint.
     *   3. Create a user account for that token if one doesn't exist yet.
     *   4. Send a "grab bill" request back to the sender with this account's
     *      vault as the destination.
     *   5. Poll for intent confirmation, copying in the verified exchange data
     *      received from the give request.
     *   6. Acknowledge the give-request message to clear it from the stream.
     *
     * Preconditions: [with] must be called first to set the owner cluster and
     * the scanned [OpenCodePayload].
     *
     * @return the confirmed [TransactionMetadata.PublicPayment] on success.
     */
    suspend fun start(): Result<TransactionMetadata.PublicPayment> {
        val ownerKey = owner
            ?: return logAndFail(GrabTransactorError.Other(message = "No owner cluster available. Did you call with() first?"))
        val data = payload
            ?: return logAndFail(GrabTransactorError.Other(message = "No payload available. Did you call with() first?"))

        val kind = payload?.kind
            ?: return logAndFail(GrabTransactorError.Other(message = "No payload kind available. Did you call with() first?"))


        return when (kind) {
            PayloadKind.Unknown -> logAndFail(GrabTransactorError.Other(message = "Unknown payload kind"))
            PayloadKind.Cash -> handleLegacyScan(ownerKey, data)
            PayloadKind.MultiMintCash -> handleMultiMintScan(ownerKey, data)
        }
    }

    fun dispose() {
        owner = null
        payload = null

        scope.cancel()
    }

    private suspend fun handleLegacyScan(
        ownerKey: AccountCluster,
        data: OpenCodePayload
    ): Result<TransactionMetadata.PublicPayment> {
        return requestGrab(ownerKey, data)
    }

    private suspend fun handleMultiMintScan(
        ownerKey: AccountCluster,
        data: OpenCodePayload
    ): Result<TransactionMetadata.SendPublicPayment> {
        // 1. Wait for the give request from the sender so we can determine what mint we are operating on
        val (messageId, giveRequestMint, exchangeData) = messagingController.pollForGiveRequest(data.rendezvous)
            .getOrNull()
            ?: return logAndFail(GiveTransactorError.Other(message = "No give request found for rendezvous"))

        // 2. Utilize the mint from the give request to get the Token metadata
        val token = tokenProvider.getTokenMetadata(giveRequestMint)
            .getOrNull()?.token
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
            requestGrab(tokenizedCluster, data)
            messagingController.sendRequestToGrabBill(
                destination = tokenizedCluster.vaultPublicKey,
                payload = data
            )
        }.fold(
            onSuccess = {
                // 5. Wait for confirmation
                transactionController.pollIntentMetadata<TransactionMetadata.SendPublicPayment>(
                    owner = tokenizedCluster.authority.keyPair,
                    intentId = data.rendezvous.toPublicKey(),
                    debugLogs = true
                ).map {
                    // copy in exchange data we received from querying for give request
                    it.copy(verifiedExchangeData = exchangeData)
                }.onSuccess {
                    // 6. Ack the receipt of the give request to clear it from the stream
                    messagingController.ackMessages(data.rendezvous, listOf(messageId))
                }
            }, onFailure = {
                if (it !is GrabTransactorError) {
                    ErrorUtils.handleError(it)
                }
                logAndFail(it)
            }
        )
    }

    private suspend fun requestGrab(
        owner: AccountCluster,
        data: OpenCodePayload
    ): Result<TransactionMetadata.PublicPayment> {
        return messagingController.sendRequestToGrabBill(
            destination = owner.vaultPublicKey,
            payload = data
        ).fold(
            onSuccess = {
                // 5. Wait for confirmation
                transactionController.pollIntentMetadata(
                    owner = owner.authority.keyPair,
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