package com.getcode.opencode.internal.transactors

import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.controllers.MessagingController
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.VerifiedFiatCalculator
import com.getcode.opencode.internal.extensions.exchangeDataFor
import com.getcode.opencode.internal.extensions.toPublicKey
import com.getcode.opencode.internal.manager.VerifiedState
import com.getcode.opencode.internal.network.extensions.asProtobufMessage
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.core.PayloadKind
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.transactions.TransactionMetadata
import com.getcode.opencode.utils.nonce
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.CodeServerError
import com.getcode.utils.NotifiableError
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlin.time.Duration

/**
 * Transactor for the **give** side of a peer-to-peer cash bill.
 *
 * Lifecycle: call [with] to configure the bill parameters and generate
 * a rendezvous payload, then [start] to advertise the bill on the
 * messaging stream and block until a recipient grabs it and the on-chain
 * transfer completes. Call [dispose] to tear down the coroutine scope
 * and clear state when the bill is dismissed or times out.
 */
internal class GiveBillTransactor(
    private val messagingController: MessagingController,
    private val transactionController: TransactionController,
    private val scope: CoroutineScope,
    private val payloadFactory: PayloadFactory,
    private val verifiedFiatCalculator: VerifiedFiatCalculator,
) : Transactor<GiveBillTransactor.GiveTransactorError>("Transactor::Give") {
    private var token: Token? = null
    private var amount: LocalFiat? = null
    private var exchangeDataTimeout: Duration? = null
    private var owner: AccountCluster? = null

    private var rendezvousKey: KeyPair? = null
    private var receivingAccount: PublicKey? = null

    private var providedVerifiedState: VerifiedState? = null

    var presentationData: BillPresentationData = BillPresentationData(emptyList(), emptyList())
        private set

    /**
     * Configures this transactor for a new bill and generates the rendezvous
     * payload. Must be called before [start].
     *
     * @param providedNonce optional nonce to reuse from a previous presentation
     *   of the same bill. When `null` a fresh random nonce is generated.
     */
    fun with(
        token: Token,
        amount: LocalFiat,
        owner: AccountCluster,
        billExchangeDataTimeout: Duration?,
        verifiedState: VerifiedState?,
        providedNonce: List<Byte>? = null,
    ) {
        this.token = token
        this.amount = amount
        this.exchangeDataTimeout = billExchangeDataTimeout
        this.owner = owner
        this.providedVerifiedState = verifiedState

        receivingAccount = null

        val resolvedNonce = providedNonce ?: nonce

        val payloadResult = payloadFactory.create(
            kind = PayloadKind.MultiMintCash,
            value = amount.nativeAmount,
            nonce = resolvedNonce
        )

        rendezvousKey = payloadResult.rendezvous
        presentationData = BillPresentationData(data = payloadResult.codeData, nonce = resolvedNonce)
    }

    /**
     * Presents a cash bill and waits for a recipient to claim it via peer-to-peer
     * messaging, then executes the on-chain transfer.
     *
     * Flow:
     *  1. Resolve a [VerifiedState] for the currency/token pair using a fallback
     *     chain: provided state -> local proto store -> live mint data fetch.
     *  2. Compute exchange data from the verified state (fails if the rate has
     *     expired past [exchangeDataTimeout]).
     *  3. Publish a "give bill" request on the rendezvous messaging stream,
     *     advertising the token mint and exchange data to potential recipients.
     *  4. Block until a "grab bill" response arrives on the same rendezvous stream.
     *  5. Verify the grab request's destination signature against the rendezvous
     *     key to ensure the destination hasn't been tampered with.
     *  6. Guard against duplicate transfers (same receiving account seen twice).
     *  7. Transfer funds from the sender's token vault to the recipient's
     *     destination account.
     *  8. Poll for the intent metadata confirmation from the server.
     *
     * Preconditions: [with] must be called first to set the token, amount, owner,
     * and (optionally) a pre-resolved [VerifiedState].
     *
     * @return the confirmed [TransactionMetadata.SendPublicPayment] on success.
     */
    suspend fun start(): Result<TransactionMetadata.SendPublicPayment> {
        if (!scope.isActive) {
            return logAndFail(GiveTransactorError.Other(message = "Transactor was disposed"))
        }

        val ownerKey = owner
            ?: return logAndFail(GiveTransactorError.Other(message = "No owner key. Did you call with() first?"))
        val desiredToken = token
            ?: return logAndFail(GiveTransactorError.Other(message = "No token. Did you call with() first?"))
        val rendezvous = rendezvousKey
            ?: return logAndFail(GiveTransactorError.Other(message = "No rendezvous key. Did you call with() first?"))
        val sendingAmount = amount
            ?: return logAndFail(GiveTransactorError.Other(message = "No amount. Did you call with() first?"))

        val initialState = providedVerifiedState
            ?: verifiedFiatCalculator.resolveVerifiedState(sendingAmount.rate.currency, desiredToken.address)
            ?: return logAndFail(GiveTransactorError.Other("Failed to get verified state"))

        val (verifiedState, exchangeData) = initialState.exchangeDataFor(
            amount = sendingAmount,
            mint = desiredToken.address,
            billExchangeDataTimeout = exchangeDataTimeout
        )?.let { initialState to it }
            ?: run {
                // Rate expired — attempt to resolve a fresh verified state
                val freshState = verifiedFiatCalculator.resolveVerifiedState(
                    sendingAmount.rate.currency, desiredToken.address
                ) ?: return logAndFail(GiveTransactorError.ExchangeRateExpiredException())

                val freshExchange = freshState.exchangeDataFor(
                    amount = sendingAmount,
                    mint = desiredToken.address,
                    billExchangeDataTimeout = null // relaxed — we just fetched this rate
                ) ?: return logAndFail(GiveTransactorError.ExchangeRateExpiredException())

                freshState to freshExchange
            }

        // 1. Send request to "give" the bill to the recipient.
        // This provides the recipient with the desired token mint of the cash.
        // If this fails, bail out immediately — the receiver never got the
        // advertisement so the stream will never deliver a grab request.
        messagingController.sendRequestToGiveBill(desiredToken.address, rendezvous, exchangeData)
            .onSuccess {
                trace(
                    tag = "Messaging",
                    message = "Successfully sent request to give bill for ${desiredToken.symbol}",
                    type = TraceType.Log
                )
            }.onFailure { cause ->
                return logAndFail(cause) {
                    "token" to desiredToken.symbol
                }
            }

        trace(
            tag = "Messaging",
            message = "Waiting for request to grab bill for ${desiredToken.symbol} on ${rendezvous.publicKey}",
            type = TraceType.Log
        )

        // 2. Wait for recipient to grab the bill
        val transferRequest = messagingController.awaitRequestToGrabBill(scope, rendezvous)
            ?: return logAndFail(GiveTransactorError.NoGrabReceived())


        // 3. Validate that destination hasn't been tampered with by
        // verifying the signature matches one that has been signed
        // with the rendezvous key.
        val data = transferRequest.asProtobufMessage().toByteArray()
        val isValid = rendezvous.verify(transferRequest.signature.byteArray, data)

        if (!isValid) {
            return logAndFail(GiveTransactorError.DestinationSignatureInvalidException())
        }

        if (receivingAccount == transferRequest.account) {
            // Ensure that we're processing one, and only one
            // transaction for each instance of SendTransaction.
            // Completion will be called by the first invocation
            // of this function.
            return logAndFail(GiveTransactorError.DuplicateTransferException())
        }

        val sendingVault = ownerKey.withTimelockForToken(desiredToken)

        receivingAccount = transferRequest.account

        val transferExchangeData = verifiedState.exchangeDataFor(
            amount = sendingAmount,
            mint = desiredToken.address,
            billExchangeDataTimeout = exchangeDataTimeout
        ) ?: exchangeData


        // 4. Send the funds to destination
        return transactionController.transfer(
            scope = scope,
            amount = amount!!,
            mint = desiredToken.address,
            source = sendingVault,
            destination = transferRequest.account,
            rendezvous = rendezvous.toPublicKey(),
            exchangeData = transferExchangeData,
        ).fold(
            onSuccess = {
                transactionController.pollIntentMetadata(
                    owner = sendingVault.authority.keyPair,
                    intentId = it.id
                )
            },
            onFailure = {
                logAndFail(it)
            }
        )
    }

    /** Cancels the coroutine scope and clears all held state. */
    fun dispose() {
        scope.cancel()
        owner = null
        presentationData = BillPresentationData(emptyList(), emptyList())
        rendezvousKey = null
        receivingAccount = null
        token = null
        providedVerifiedState = null
    }

    sealed class GiveTransactorError(
        override val message: String? = null,
        override val cause: Throwable? = null
    ) : CodeServerError(message, cause) {
        class DuplicateTransferException : GiveTransactorError(message = "Duplicate Transfer"), NotifiableError
        class DestinationSignatureInvalidException : GiveTransactorError(message = "Destination signature invalid"), NotifiableError
        class ExchangeRateExpiredException : GiveTransactorError(message = "Exchange rate expired"), NotifiableError
        class NoGrabReceived : GiveTransactorError(message = "No message received")
        data class Other(
            override val message: String? = null,
            override val cause: Throwable? = null
        ) : GiveTransactorError(message, cause), NotifiableError
    }
}