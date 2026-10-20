package com.flipcash.shared.payments.boost

import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.services.controllers.ResolverController
import com.flipcash.services.models.buildReactionBoostMetadata
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.Emoji
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.exchange.VerifiedFiat
import com.getcode.opencode.exchange.VerifiedFiatCalculator
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.core.errors.ComputeVerifiedFiatError
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
    private val resolverController: ResolverController,
    private val transactionController: TransactionController,
    private val tokenCoordinator: TokenCoordinator,
    private val verifiedFiatCalculator: VerifiedFiatCalculator,
    private val exchange: Exchange,
) {

    /**
     * Boosts the viewer's existing [emoji] reaction on [messageId] in [chatId] with [plan], paid in
     * [token] from [source]. Every share is priced before anything is sent.
     */
    suspend fun boost(
        chatId: ChatId,
        messageId: Long,
        emoji: String,
        plan: ReactionBoostPlan.Pay,
        token: Token,
        source: AccountCluster,
    ): Result<Unit> {
        val priced = price(plan, token).getOrElse { return Result.failure(it) }
        return pay(chatId, messageId, emoji, priced, token, source)
    }

    /**
     * Each share's quarks from its own USD amount, all at one verified state: a multi-payment
     * intent carries one rate and reserve for every share. A refresh between two computes is
     * retried once before giving up as stale.
     */
    private suspend fun price(plan: ReactionBoostPlan.Pay, token: Token): Result<List<PricedShare>> {
        repeat(2) {
            val priced = plan.shares.map { share ->
                verifiedFiatCalculator.compute(
                    amount = share.usd(),
                    token = token,
                    balance = tokenCoordinator.balanceForToken(token),
                    rate = exchange.rateForUsd(),
                ).map { PricedShare(share, it) }.getOrElse { return Result.failure(it) }
            }
            val states = priced.map { it.amount.verifiedState }
            if (states.all { it != null && it == states.first() }) return Result.success(priced)
        }
        return Result.failure(ComputeVerifiedFiatError.StaleRate())
    }

    private suspend fun pay(
        chatId: ChatId,
        messageId: Long,
        emoji: String,
        priced: List<PricedShare>,
        token: Token,
        source: AccountCluster,
    ): Result<Unit> = runCatching {
        val metadata = buildReactionBoostMetadata(chatId, messageId, Emoji(emoji))
        val destinations = priced.map { resolverController.resolve(it.share.payee).getOrThrow() }

        if (priced.size == 1) {
            transactionController.directTransfer(
                amount = priced.single().amount,
                token = token,
                source = source,
                destinationOwner = destinations.single(),
                appMetadata = metadata,
            ).getOrThrow()
        } else {
            transactionController.directMultiTransfer(
                payments = destinations.zip(priced.map { it.amount.localFiat }),
                verifiedState = requireNotNull(priced.first().amount.verifiedState),
                token = token,
                source = source,
                appMetadata = metadata,
            ).getOrThrow()
        }
        priced.forEach { tokenCoordinator.subtract(token, it.amount.localFiat) }
    }

    private class PricedShare(val share: BoostShare, val amount: VerifiedFiat)
}

/** This share as a USD amount, exact to the cent. */
internal fun BoostShare.usd(): Fiat = centsToUsd(cents)

/** [cents] whole USD cents as a [Fiat], without going through a double. */
fun centsToUsd(cents: Long): Fiat =
    Fiat(stringAmount = BigDecimal.valueOf(cents, 2).toPlainString(), currencyCode = CurrencyCode.USD)
