package com.flipcash.shared.payments.request

import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.services.controllers.ResolverController
import com.flipcash.services.models.buildRequestedPaymentMetadata
import com.flipcash.services.models.paymentrequest.PaymentIntentId
import com.flipcash.services.models.paymentrequest.PaymentRequest
import com.flipcash.services.models.paymentrequest.Share
import com.flipcash.shared.chat.PaymentRequests
import com.flipcash.shared.payments.SharedStatePayment
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.exchange.VerifiedFiatCalculator
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.core.errors.ComputeVerifiedFiatError
import com.getcode.opencode.model.financial.Token
import com.getcode.solana.keys.PublicKey
import kotlin.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pays the viewer's share of a payment request: one public payment to the creator, carrying
 * requested-payment app metadata, under the intent id the server issued for that share.
 *
 * Call [prepare] immediately before showing the pay sheet, and again before any retry after an
 * unknown outcome: it refetches the request, so a share that landed meanwhile comes back as
 * [ShareToPay.NothingToPay] instead of paying twice. Paying twice under one id lands once anyway.
 *
 * No UI. Balance and send-limit checks on the share are the caller's.
 */
@Singleton
class PaymentRequestPaymentDelegate @Inject constructor(
    resolverController: ResolverController,
    transactionController: TransactionController,
    tokenCoordinator: TokenCoordinator,
    verifiedFiatCalculator: VerifiedFiatCalculator,
    private val exchange: Exchange,
    private val paymentRequests: PaymentRequests,
) {

    private val payment = SharedStatePayment(
        resolverController, transactionController, tokenCoordinator, verifiedFiatCalculator,
    )

    /** Refetches [requestId] and says whether [viewer] has a share to pay now. */
    suspend fun prepare(requestId: ID, viewer: ID): Result<ShareToPay> =
        paymentRequests.fetch(requestId).map { details ->
            val request = details.paymentRequest
            val intentId = details.paymentIntentId
            val share = request.split?.shareOf(viewer)
            when {
                !request.isOpen(Clock.System.now()) -> ShareToPay.Closed(request)
                intentId == null || share == null || share.isPaid -> ShareToPay.NothingToPay(request)
                else -> ShareToPay.Ready(request, share, intentId)
            }
        }

    /**
     * Pays [ready]'s share in the request's currency, priced into [token] from [source]. Refetches
     * the request after the payment lands so the row reads as paid.
     */
    suspend fun pay(ready: ShareToPay.Ready, token: Token, source: AccountCluster): Result<Unit> {
        val request = ready.request
        val split = requireNotNull(request.split)
        val rate = exchange.rateFor(split.currency)
            ?: return Result.failure(ComputeVerifiedFiatError.StaleRate())
        val priced = payment.price(listOf(ready.share.amount), token, rate)
            .getOrElse { return Result.failure(it) }
        return payment.pay(
            payees = listOf(request.creator),
            priced = priced,
            token = token,
            source = source,
            appMetadata = buildRequestedPaymentMetadata(request.chatId, request.id.toByteArray()),
            intentId = PublicKey(ready.intentId.bytes.toList()),
        ).onSuccess { paymentRequests.fetch(request.id) }
    }
}

/** What [PaymentRequestPaymentDelegate.prepare] found. */
sealed interface ShareToPay {
    val request: PaymentRequest

    /** The viewer owes [share]; pay it under [intentId], which never prints. */
    class Ready(
        override val request: PaymentRequest,
        val share: Share,
        val intentId: PaymentIntentId,
    ) : ShareToPay {
        override fun toString(): String = "Ready(request=${request.id.size} bytes, share=$share)"
    }

    /** The viewer has no unpaid share: never asked, or already paid. */
    data class NothingToPay(override val request: PaymentRequest) : ShareToPay

    /** The request is no longer open: "Request Closed". */
    data class Closed(override val request: PaymentRequest) : ShareToPay
}
