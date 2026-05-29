package com.getcode.opencode.internal.transactors

import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.internal.extensions.toPublicKey
import com.getcode.opencode.internal.manager.VerifiedState
import com.getcode.opencode.internal.network.api.intents.IntentRemoteSend
import com.getcode.opencode.internal.transactors.GiveBillTransactor.GiveTransactorError
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.GiftCardAccount
import com.getcode.opencode.model.core.PayloadKind
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.utils.nonce
import com.getcode.utils.CodeServerError
import com.getcode.utils.NotifiableError

/**
 * Transactor for **sending a cash link** (gift card).
 *
 * Lifecycle: call [with] to set the gift card account, amount, token,
 * and owner, then [start] to submit the remote-send intent that funds
 * the gift card on-chain. Call [dispose] to clear state when finished.
 */
internal class SendGiftCardTransactor(
    private val transactionController: TransactionController,
    private val payloadFactory: PayloadFactory,
): Transactor<SendGiftCardTransactor.SendTransactorError>("Transactor::Send") {
    private var giftCardAccount: GiftCardAccount? = null
    private var token: Token? = null
    private var amount: LocalFiat? = null
    private var owner: AccountCluster? = null
    private var verifiedState: VerifiedState? = null

    private var rendezvousKey: KeyPair? = null

    /** Configures this transactor for a new gift card send. Must be called before [start]. */
    fun with(giftCard: GiftCardAccount, amount: LocalFiat, token: Token, owner: AccountCluster, verifiedState: VerifiedState) {
        this.giftCardAccount = giftCard
        this.token = token
        this.amount = amount
        this.owner = owner
        this.verifiedState = verifiedState

        val payloadResult = payloadFactory.create(
            kind = PayloadKind.MultiMintCash,
            value = amount.nativeAmount,
            nonce = nonce
        )

        rendezvousKey = payloadResult.rendezvous
    }

    /**
     * Funds a gift card for remote sending — submits a remote-send intent so the
     * gift card can later be claimed by a recipient via link or QR code.
     *
     * Flow:
     *  1. Resolve the sender's token vault (timelock account for the target token).
     *  2. Submit a remote-send intent to the server, transferring funds from
     *     the sender's vault into the pre-created [GiftCardAccount].
     *
     * Preconditions: [with] must be called first to set the gift card account,
     * amount, token, and owner cluster.
     *
     * @return the [IntentRemoteSend] details on success.
     */
    suspend fun start(): Result<IntentRemoteSend> {
       val rendezvous = rendezvousKey
            ?: return logAndFail(GiveTransactorError.Other(message = "No rendezvous key. Did you call with() first?"))
        val giftCard = giftCardAccount
            ?: return logAndFail(GiveTransactorError.Other(message = "No gift card account. Did you call with() first?"))
        val desiredToken = token
            ?: return logAndFail(GiveTransactorError.Other(message = "No token mint. Did you call with() first?"))

        val ownerKey = owner
            ?: return logAndFail(GiveTransactorError.Other(message = "No owner key. Did you call with() first?"))

        val pinnedState = verifiedState
            ?: return logAndFail(GiveTransactorError.Other(message = "No verified state. Did you call with() first?"))

        val source = ownerKey.withTimelockForToken(desiredToken)

        return transactionController.remoteSend(
            rendezvous = rendezvous.toPublicKey(),
            owner = ownerKey,
            source = source,
            amount = amount!!,
            giftCard = giftCard,
            token = desiredToken,
            verifiedState = pinnedState,
        ).map { it as IntentRemoteSend }
            .fold(
                onSuccess = { Result.success(it) },
                onFailure = {
                    logAndFail(it)
                }
            )

    }

    /** Clears all held state. */
    fun dispose() {
        amount = null
        giftCardAccount = null
        owner = null
        rendezvousKey = null
    }

    sealed class SendTransactorError(
        override val message: String? = null,
        override val cause: Throwable? = null
    ) : CodeServerError(message, cause) {
        data class Other(
            override val message: String? = null,
            override val cause: Throwable? = null
        ) : SendTransactorError(message, cause), NotifiableError
    }
}