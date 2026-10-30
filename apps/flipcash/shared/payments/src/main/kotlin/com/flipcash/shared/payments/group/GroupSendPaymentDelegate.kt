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
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Token
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pays members of a group from one cash message, split by [GroupSendSplit]: one recipient is a
 * single payment, 2 to 10 are one multi-payment in selection order. Both carry group-payment app
 * metadata, so the server posts the message into the group.
 *
 * No UI. The caller checks the balance against the sum of [GroupSendPlan.Pay.parts] (what the sender
 * pays, which is less than the entered total when the sender is in the split) and any send limit,
 * before it calls [send].
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
     * Pays each of [recipients] its part of [plan], in [currency], from [source] in [token].
     * [recipients] are in selection order, matching [GroupSendPlan.Pay.parts]. Every part is priced
     * from its own amount, at one verified state, before anything is sent.
     */
    suspend fun send(
        chatId: ChatId,
        recipients: List<ID>,
        plan: GroupSendPlan.Pay,
        currency: CurrencyCode,
        token: Token,
        source: AccountCluster,
    ): Result<Unit> {
        require(recipients.size == plan.parts.size) {
            "${recipients.size} recipients for ${plan.parts.size} parts"
        }
        val rate = exchange.rateFor(currency)
            ?: return Result.failure(ComputeVerifiedFiatError.StaleRate())
        val priced = payment.price(plan.parts.map { GroupSendSplit.fiat(it, currency) }, token, rate)
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
