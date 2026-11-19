package com.flipcash.shared.payments.request

import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.services.controllers.ResolverController
import com.flipcash.services.models.GetPaymentIntentIdError
import com.flipcash.services.models.buildRequestedPaymentMetadata
import com.flipcash.services.models.paymentrequest.CollectionAmount
import com.flipcash.services.models.paymentrequest.PaymentIntentId
import com.flipcash.services.models.paymentrequest.PaymentRequest
import com.flipcash.shared.chat.PaymentRequests
import com.flipcash.shared.payments.SharedStatePayment
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.exchange.VerifiedFiatCalculator
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.core.errors.ComputeVerifiedFiatError
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Token
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.base58
import com.getcode.utils.LiveSecrets
import com.getcode.utils.TraceType
import com.getcode.utils.maskedSuffix
import com.getcode.utils.trace
import kotlin.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pays the viewer's part of a payment request: one public payment to the creator, carrying
 * requested-payment app metadata, under the intent id the server issued for it. For a split that
 * part is the viewer's share; for a collection it is the collection's fixed amount.
 *
 * Call [prepare] immediately before showing the pay sheet, and again before any retry after an
 * unknown outcome: for a split it refetches the request, and for a collection it asks
 * `GetPaymentIntentId` again, so a payment that landed meanwhile comes back as
 * [ShareToPay.NothingToPay] instead of paying twice. Paying twice under one id lands once anyway.
 *
 * No UI. Balance and send-limit checks on the amount are the caller's.
 */
@Singleton
class PaymentRequestPaymentDelegate @Inject constructor(
    resolverController: ResolverController,
    transactionController: TransactionController,
    tokenCoordinator: TokenCoordinator,
    verifiedFiatCalculator: VerifiedFiatCalculator,
    private val exchange: Exchange,
    private val paymentRequests: PaymentRequests,
    private val funder: CollectionFunder,
) {

    private val payment = SharedStatePayment(
        resolverController, transactionController, tokenCoordinator, verifiedFiatCalculator,
    )

    /** Where the pay outcome is logged; tests swap it to read the line. */
    @androidx.annotation.VisibleForTesting
    internal var log: (message: String, type: TraceType) -> Unit = { message, type ->
        trace(tag = "PaymentRequest", message = message, type = type)
    }

    /** Says whether [viewer] has something to pay into [requestId] now. */
    suspend fun prepare(requestId: ID, viewer: ID): Result<ShareToPay> {
        // A collection is known from the copy its screen shows, so paying starts with
        // GetPaymentIntentId rather than a refetch.
        paymentRequests.state.value[requestId]?.takeIf { it.collection != null }?.let { return prepareCollection(it) }
        val details = paymentRequests.fetch(requestId).getOrElse { return Result.failure(it) }
        val request = details.paymentRequest
        if (request.collection != null) return prepareCollection(request)
        val intentId = details.paymentIntentId
        val share = request.split?.shareOf(viewer)
        return Result.success(
            when {
                !request.isOpen(Clock.System.now()) -> ShareToPay.Closed(request)
                intentId == null || share == null || share.isPaid -> ShareToPay.NothingToPay(request)
                else -> ShareToPay.Ready(request, share.amount, intentId)
            }
        )
    }

    /**
     * `GetPaymentIntentId` for a collection, each refusal mapped to what the screen shows. A refusal
     * that changes what the viewer sees refetches first, so the result carries the fresh copy.
     */
    private suspend fun prepareCollection(request: PaymentRequest): Result<ShareToPay> {
        val amount = (request.collection?.amount as? CollectionAmount.Fixed)?.amount
            ?: return Result.success(ShareToPay.NothingToPay(request))
        val intentId = paymentRequests.paymentIntentId(request.id).getOrElse { error ->
            return when (error) {
                is GetPaymentIntentIdError.NotOpen -> Result.success(ShareToPay.Closed(refetched(request)))
                is GetPaymentIntentIdError.AlreadyPaid,
                is GetPaymentIntentIdError.NotAPayer -> {
                    // Conversions kept for a retry can't be needed once there is nothing to pay.
                    funder.forget(request.id)
                    Result.success(ShareToPay.NothingToPay(refetched(request)))
                }
                is GetPaymentIntentIdError.Denied,
                is GetPaymentIntentIdError.NotFound -> Result.success(ShareToPay.Unavailable(request))
                else -> Result.failure(error)
            }
        }
        return Result.success(ShareToPay.Ready(request, amount, intentId))
    }

    private suspend fun refetched(request: PaymentRequest): PaymentRequest {
        val fresh = paymentRequests.fetch(request.id).getOrNull()?.paymentRequest ?: return request
        if (fresh.collection != null) paymentRequests.loadPayers(fresh.id)
        return fresh
    }

    /**
     * Pays [ready]'s amount in the request's currency, priced into [token] from [source]. Refetches
     * the request after the payment lands so its row reads as paid.
     *
     * With [fund], a collection short in [token] is first topped up by converting other balances
     * (see [CollectionFunder]); it fails with [CollectionFundingError.Short] when that can't cover
     * it. Without it, [token] must already hold the amount.
     */
    suspend fun pay(
        ready: ShareToPay.Ready,
        token: Token,
        source: AccountCluster,
        fund: Boolean = false,
    ): Result<Unit> {
        val request = ready.request
        val collection = request.collection != null
        val currency = requireNotNull(request.currency)
        val funding = collection && fund
        if (funding) funder.fund(request.id, ready.amount, token, source).onFailure { return Result.failure(it) }
        val rate = exchange.rateFor(currency)
            ?: return Result.failure(ComputeVerifiedFiatError.StaleRate())
        val priced = payment.price(listOf(ready.amount), token, rate)
            .getOrElse { return Result.failure(it) }
        val intentId = PublicKey(ready.intentId.bytes.toList())
        // Until the payment lands, anyone who reads the id can use it up, so lower layers' logs
        // see only its last characters.
        val idBase58 = intentId.base58()
        LiveSecrets.hold(idBase58)
        val paid = payment.pay(
            payees = listOf(request.creator),
            priced = priced,
            token = token,
            source = source,
            appMetadata = buildRequestedPaymentMetadata(request.groupChatId, request.id.toByteArray()),
            intentId = intentId,
        )
        if (collection) {
            // A collection's id never reaches a log, whatever the outcome, so it stays held: the
            // call's bodies are logged when it closes, which can come after this resumes, and later
            // responses carry the same id in the viewer's paid record. This delegate keeps no copy:
            // a retry asks for the id again.
            return paid.onSuccess {
                log("paid collection", TraceType.Log)
                if (funding) funder.paid(request.id, token)
                paymentRequests.fetch(request.id)
                paymentRequests.loadPayers(request.id)
            }.onFailure {
                log("collection payment failed", TraceType.Error)
            }
        }
        // A split's share logs its id in full once paid, when it is harmless.
        return paid.onSuccess {
            LiveSecrets.release(idBase58)
            log("paid share under intent $idBase58", TraceType.Log)
            paymentRequests.fetch(request.id)
        }.onFailure {
            log("share payment failed under intent ${idBase58.maskedSuffix()}", TraceType.Error)
        }
    }

}

/** What [PaymentRequestPaymentDelegate.prepare] found. */
sealed interface ShareToPay {
    val request: PaymentRequest

    /**
     * The viewer owes [amount] in the request's currency: a split's unpaid share, or a collection's
     * fixed amount. Pay it under [intentId], which never prints.
     */
    class Ready(
        override val request: PaymentRequest,
        val amount: Fiat,
        val intentId: PaymentIntentId,
    ) : ShareToPay {
        override fun toString(): String = "Ready(request=${request.id.size} bytes, amount=$amount)"
    }

    /** The viewer has nothing to pay: never asked, the creator, or already paid. */
    data class NothingToPay(override val request: PaymentRequest) : ShareToPay

    /** The request is no longer open: "Request Closed". */
    data class Closed(override val request: PaymentRequest) : ShareToPay

    /** The viewer can no longer reach the request: "This request is no longer available." */
    data class Unavailable(override val request: PaymentRequest) : ShareToPay
}
