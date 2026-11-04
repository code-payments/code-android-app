package com.flipcash.shared.payments.sendback

import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.services.controllers.ResolverController
import com.flipcash.services.models.buildReturnPaymentMetadata
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.paymentrequest.PaymentIntentId
import com.flipcash.shared.payments.SharedStatePayment
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.exchange.VerifiedFiat
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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends back a DM's opening payment: one public payment of the original's fiat amount, in its
 * currency and mint, to the person who paid it, carrying `ChatMetadata.ReturnPayment` and submitted
 * under the intent id `GetCashMessageReturnStatus` issued for that message.
 *
 * The amount is priced at today's rate, so the token count may differ from the original's.
 *
 * No UI, no status calls, and no balance check: those are the caller's.
 */
@Singleton
class SendBackPaymentDelegate @Inject constructor(
    resolverController: ResolverController,
    transactionController: TransactionController,
    tokenCoordinator: TokenCoordinator,
    private val verifiedFiatCalculator: VerifiedFiatCalculator,
    private val exchange: Exchange,
) {

    private val payment = SharedStatePayment(
        resolverController, transactionController, tokenCoordinator, verifiedFiatCalculator,
    )

    /** Where the pay outcome is logged; tests swap it to read the line. */
    @androidx.annotation.VisibleForTesting
    internal var log: (message: String, type: TraceType) -> Unit = { message, type ->
        trace(tag = "SendBack", message = message, type = type)
    }

    /**
     * [amount] in [token] at today's rate, uncapped by any balance, so its token quarks
     * (`localFiat.underlyingTokenAmount.quarks`) are what the return needs.
     */
    suspend fun price(amount: Fiat, token: Token): Result<VerifiedFiat> {
        val rate = exchange.rateFor(amount.currencyCode)
            ?: return Result.failure(ComputeVerifiedFiatError.StaleRate())
        return verifiedFiatCalculator.compute(amount = amount, token = token, balance = null, rate = rate)
    }

    /**
     * Pays [priced] in [token] from [source] to [payee], the original payment's sender, as the
     * return of [messageId] in the DM [chatId], under [intentId].
     *
     * [priced] is [price]'s answer for the balance check just made, sent as is: pricing again here
     * could read a newer rate and pay a different token count from the one checked.
     *
     * The id is only ever logged masked, on either outcome: the spec keeps it out of every log.
     */
    suspend fun pay(
        chatId: ChatId,
        messageId: Long,
        payee: ID,
        priced: VerifiedFiat,
        token: Token,
        source: AccountCluster,
        intentId: PaymentIntentId,
    ): Result<Unit> {
        val key = PublicKey(intentId.bytes.toList())
        val masked = key.base58().also(LiveSecrets::hold).maskedSuffix()
        return payment.pay(
            payees = listOf(payee),
            priced = listOf(priced),
            token = token,
            source = source,
            appMetadata = buildReturnPaymentMetadata(chatId, messageId),
            intentId = key,
        ).onSuccess {
            log("sent back under intent $masked", TraceType.Log)
        }.onFailure {
            log("send back failed under intent $masked", TraceType.Error)
        }
    }
}
