package com.flipcash.shared.payments.group

import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.services.controllers.ResolverController
import com.flipcash.services.models.buildGroupPaymentMetadata
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.payments.SharedStatePayment
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.exchange.VerifiedFiatCalculator
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.core.errors.ComputeVerifiedFiatError
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Token
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pays members of a group from one cash message, each the same amount: one recipient is a
 * single payment, 2 to 10 are one multi-payment in selection order. Both carry group-payment app
 * metadata, so the server posts the message into the group.
 *
 * No UI. The caller checks the balance against the amount times the recipient count (what the sender
 * pays) and any send limit, before it calls [send].
 */
@Singleton
class GroupSendPaymentDelegate @Inject constructor(
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
     * Pays each of [recipients] [amount], from [source] in [token]. [recipients] are in selection
     * order, which is the order of the payments. Every payment is priced from [amount], at one
     * verified state, before anything is sent.
     */
    suspend fun send(
        chatId: ChatId,
        recipients: List<ID>,
        amount: Fiat,
        token: Token,
        source: AccountCluster,
    ): Result<Unit> {
        require(recipients.isNotEmpty()) { "no recipients" }
        val rate = exchange.rateFor(amount.currencyCode)
            ?: return Result.failure(ComputeVerifiedFiatError.StaleRate())
        val priced = payment.price(List(recipients.size) { amount }, token, rate)
            .getOrElse { return Result.failure(it) }
        return payment.pay(
            payees = recipients,
            priced = priced,
            token = token,
            source = source,
            appMetadata = buildGroupPaymentMetadata(chatId),
        )
    }
}
