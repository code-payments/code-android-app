package com.flipcash.shared.payments.boost

import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.shared.payments.SharedStatePayment
import com.flipcash.services.controllers.ResolverController
import com.flipcash.services.models.buildReactionBoostMetadata
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.Emoji
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.exchange.VerifiedFiatCalculator
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Token
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pays a reaction boost planned by [ReactionBoostSplit]: one intent carrying the boost's app
 * metadata, naming a reaction the viewer has already made. Reacting stays free and separate, so
 * this never adds or removes a reaction. A boost is priced in USD because the server reports it as
 * `boost_usd`.
 *
 * No UI. Balance and send-limit checks on the total are the caller's, before it calls [boost]:
 * each share is priced against the whole balance, so two shares can each fit where their sum does
 * not.
 */
@Singleton
class ReactionBoostPaymentDelegate @Inject constructor(
    resolverController: ResolverController,
    transactionController: TransactionController,
    tokenCoordinator: TokenCoordinator,
    verifiedFiatCalculator: VerifiedFiatCalculator,
    private val exchange: Exchange,
) {

    private val payment = SharedStatePayment(
        resolverController, transactionController, tokenCoordinator, verifiedFiatCalculator,
    )

    /**
     * Boosts the viewer's existing [emoji] reaction on [messageId] in [chatId] with [plan], paid in
     * [token] from [source]. Every share is priced, at one verified state, before anything is sent.
     */
    suspend fun boost(
        chatId: ChatId,
        messageId: Long,
        emoji: String,
        plan: ReactionBoostPlan.Pay,
        token: Token,
        source: AccountCluster,
    ): Result<Unit> {
        val priced = payment.price(plan.shares.map { it.usd() }, token, exchange.rateForUsd())
            .getOrElse { return Result.failure(it) }
        return payment.pay(
            payees = plan.shares.map { it.payee },
            priced = priced,
            token = token,
            source = source,
            appMetadata = buildReactionBoostMetadata(chatId, messageId, Emoji(emoji)),
        )
    }
}

/** This share as a USD amount, exact to the cent. */
internal fun BoostShare.usd(): Fiat = centsToUsd(cents)

/** [cents] whole USD cents as a [Fiat], without going through a double. */
fun centsToUsd(cents: Long): Fiat =
    Fiat(stringAmount = BigDecimal.valueOf(cents, 2).toPlainString(), currencyCode = CurrencyCode.USD)
