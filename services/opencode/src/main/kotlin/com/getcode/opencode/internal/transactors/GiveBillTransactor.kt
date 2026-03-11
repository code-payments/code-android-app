package com.getcode.opencode.internal.transactors

import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.controllers.CurrencyController
import com.getcode.opencode.controllers.MessagingController
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.internal.extensions.exchangeDataFor
import com.getcode.opencode.internal.extensions.toPublicKey
import com.getcode.opencode.internal.manager.VerifiedProtoManager
import com.getcode.opencode.internal.manager.VerifiedState
import com.getcode.opencode.internal.network.extensions.asProtobufMessage
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.core.OpenCodePayload
import com.getcode.opencode.model.core.PayloadKind
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.transactions.TransactionMetadata
import com.getcode.opencode.utils.nonce
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.CodeServerError
import com.getcode.utils.TraceType
import com.getcode.utils.base58
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlin.time.Duration

internal class GiveBillTransactor(
    private val currencyController: CurrencyController,
    private val messagingController: MessagingController,
    private val transactionController: TransactionController,
    private val scope: CoroutineScope,
    private val verifiedProtoManager: VerifiedProtoManager,
) : Transactor<GiveBillTransactor.GiveTransactorError>("Transactor::Give") {
    private var token: Token? = null
    private var amount: LocalFiat? = null
    private var exchangeDataTimeout: Duration? = null
    private var owner: AccountCluster? = null

    private var rendezvousKey: KeyPair? = null
    private var receivingAccount: PublicKey? = null

    private var providedVerifiedState: VerifiedState? = null

    private var payload: OpenCodePayload = OpenCodePayload.Empty
    var data: List<Byte> = emptyList()
        private set

    fun with(
        token: Token,
        amount: LocalFiat,
        owner: AccountCluster,
        billExchangeDataTimeout: Duration?,
        verifiedState: VerifiedState?
    ) {
        this.token = token
        this.amount = amount
        this.exchangeDataTimeout = billExchangeDataTimeout
        this.owner = owner
        this.providedVerifiedState = verifiedState

        receivingAccount = null

        val payloadInfo = OpenCodePayload(
            kind = PayloadKind.MultiMintCash,
            value = amount.nativeAmount,
            nonce = nonce
        )

        payload = payloadInfo
        rendezvousKey = payloadInfo.rendezvous
        data = payloadInfo.codeData.toList()
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
        val ownerKey = owner
            ?: return logAndFail(GiveTransactorError.Other(message = "No owner key. Did you call with() first?"))
        val desiredToken = token
            ?: return logAndFail(GiveTransactorError.Other(message = "No token. Did you call with() first?"))
        val rendezvous = rendezvousKey
            ?: return logAndFail(GiveTransactorError.Other(message = "No rendezvous key. Did you call with() first?"))
        val sendingAmount = amount
            ?: return logAndFail(GiveTransactorError.Other(message = "No amount. Did you call with() first?"))

        val verifiedState = resolveVerifiedState(sendingAmount, desiredToken)
            ?: return logAndFail(GiveTransactorError.Other("Failed to get verified state"))

        val exchangeData = verifiedState.exchangeDataFor(
            amount = sendingAmount,
            mint = desiredToken.address,
            billExchangeDataTimeout = exchangeDataTimeout
        ) ?: return logAndFail(GiveTransactorError.ExchangeRateExpiredException())

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
            message = "Waiting for request to grab bill for ${desiredToken.symbol} on ${rendezvous.publicKeyBytes.base58}",
            type = TraceType.Log
        )

        // 2. Wait for recipient to grab the bill
        val transferRequest = messagingController.awaitRequestToGrabBill(scope, rendezvous)
            ?: return logAndFail(GiveTransactorError.Other(message = "No message received"))


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

        val transferVerifiedState = resolveVerifiedState(sendingAmount, desiredToken)
            ?: return logAndFail(GiveTransactorError.Other("Failed to get verified state"))

        val transferExchangeData = transferVerifiedState.exchangeDataFor(
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

    fun dispose() {
        owner = null
        payload = OpenCodePayload.Empty
        data = emptyList()
        rendezvousKey = null
        receivingAccount = null
        token = null

        scope.cancel()
    }

    /**
     * Resolve the verified state for the given currency/token pair using a fallback chain:
     * 1. Use the provided state directly if available
     * 2. Otherwise, check the local proto store
     * 3. If still missing, fetch live mint data (which internally persists to the store) and re-query
     */
    private suspend fun resolveVerifiedState(
        sendingAmount: LocalFiat,
        desiredToken: Token
    ): VerifiedState? {
        val currency = sendingAmount.rate.currency
        val mint = desiredToken.address
        val label = "${currency}/${desiredToken.symbol}"
        val needsReserveState = mint != Mint.usdf

        providedVerifiedState?.let {
            trace(tag = tag, message = "Using provided verified state for $label")
            return it
        }

        verifiedProtoManager.getVerifiedStateFor(currency, mint)?.let {
            if (!needsReserveState || it.reserveProto != null) {
                trace(tag = tag, message = "Resolved verified state from proto store for $label")
                return it
            }

            trace(tag = tag, message = "Proto store hit but missing reserve state for $label — fetching live mint data")
        }

        trace(tag = tag, message = "Proto store miss — fetching live mint data for ${desiredToken.symbol}")

        return currencyController.getLiveMintData(scope, mint)
            .onFailure { cause ->
                trace(
                    tag = tag,
                    message = "Live mint data fetch failed for ${desiredToken.symbol}",
                    type = TraceType.Error,
                    error = cause
                )
            }
            .map { verifiedProtoManager.getVerifiedStateFor(currency, mint) }
            .getOrNull()
            ?.also {
                trace(tag = tag, message = "Resolved verified state after live mint fetch for $label")
            }
    }


    sealed class GiveTransactorError(
        override val message: String? = null,
        override val cause: Throwable? = null
    ) : CodeServerError(message, cause) {
        class DuplicateTransferException : GiveTransactorError(message = "Duplicate Transfer")
        class DestinationSignatureInvalidException : GiveTransactorError(message = "Destination signature invalid")
        class ExchangeRateExpiredException : GiveTransactorError(message = "Exchange rate expired")
        data class Other(
            override val message: String? = null,
            override val cause: Throwable? = null
        ) : GiveTransactorError(message, cause)
    }
}