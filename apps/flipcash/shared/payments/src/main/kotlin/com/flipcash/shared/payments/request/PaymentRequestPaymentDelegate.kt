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
import com.getcode.solana.keys.base58
import com.getcode.utils.LiveSecrets
import com.getcode.utils.TraceType
import com.getcode.utils.maskedSuffix
import com.getcode.utils.trace
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

    /** Where the pay outcome is logged; tests swap it to read the line. */
    @androidx.annotation.VisibleForTesting
    internal var log: (message: String, type: TraceType) -> Unit = { message, type ->
        trace(tag = "PaymentRequest", message = message, type = type)
    }

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
        val intentId = PublicKey(ready.intentId.bytes.toList())
        // Until the share is paid, anyone who reads the id can use it up, so logs see only its
        // last characters. Once paid it is harmless and logs in full.
        val idBase58 = intentId.base58()
        LiveSecrets.hold(idBase58)
        return payment.pay(
            payees = listOf(request.creator),
            priced = priced,
            token = token,
            source = source,
            appMetadata = buildRequestedPaymentMetadata(request.chatId, request.id.toByteArray()),
            intentId = intentId,
        ).onSuccess {
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
