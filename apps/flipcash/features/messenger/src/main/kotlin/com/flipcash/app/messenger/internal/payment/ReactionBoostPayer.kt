package com.flipcash.app.messenger.internal.payment

import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.user.UserManager
import com.flipcash.shared.payments.TipPaymentDelegate
import com.flipcash.shared.payments.boost.BoostChat
import com.flipcash.shared.payments.boost.ReactionBoostPaymentDelegate
import com.flipcash.shared.payments.boost.ReactionBoostPlan
import com.flipcash.shared.payments.boost.ReactionBoostSplit
import com.flipcash.shared.payments.boost.centsToUsd
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.core.errors.ComputeVerifiedFiatError
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * The checks and alerts around paying for a reaction: who gets paid, whether the total fits the
 * balance and the send limit, and what to tell the user when it doesn't. The payment itself is
 * [ReactionBoostPaymentDelegate]'s.
 *
 * A failure that is a [StartChattingPayer.PaymentBlocked] has already been explained to the user.
 */
internal class ReactionBoostPayer @Inject constructor(
    private val startChattingPayer: StartChattingPayer,
    private val boostDelegate: ReactionBoostPaymentDelegate,
    private val tipPaymentDelegate: TipPaymentDelegate,
    private val tokenCoordinator: TokenCoordinator,
    private val exchange: Exchange,
    private val userManager: UserManager,
    private val resources: ResourceHelper,
) {

    /** What [author]'s message pays out for a boost of [totalCents] in [chat]. */
    fun plan(author: ID, chat: BoostChat, totalCents: Long): ReactionBoostPlan? {
        val payer = userManager.accountId ?: return null
        return ReactionBoostSplit.plan(payer = payer, sender = author, chat = chat, totalCents = totalCents)
    }

    suspend fun boost(
        chatId: ChatId,
        messageId: Long,
        emoji: String,
        author: ID,
        chat: BoostChat,
        totalCents: Long,
        onAddMoney: () -> Unit,
    ): Result<Unit> {
        val owner = userManager.accountCluster ?: return failed("no account")
        val plan = when (val planned = plan(author, chat, totalCents)) {
            is ReactionBoostPlan.Pay -> planned
            ReactionBoostPlan.ShareBelowMinimum -> {
                alert(R.string.error_title_amountTooSmall, R.string.error_description_amountTooSmall)
                return blocked()
            }
            ReactionBoostPlan.SelfBoost -> return failed("cannot boost own message")
            null -> return failed("no account to plan from")
        }

        val token = startChattingPayer.observeSelectedToken().first()
        val total = centsToUsd(plan.totalCents)
        val balance = tokenCoordinator.balanceForToken(token).convertingTo(exchange.rateForUsd())
        if (total.valueGreaterThan(balance)) {
            startChattingPayer.presentInsufficientBalance(onAddMoney)
            return blocked()
        }
        if (tipPaymentDelegate.exceedsSendLimit(total)) {
            alert(R.string.error_title_sendLimitReached, R.string.error_description_sendLimitReached)
            return blocked()
        }

        return boostDelegate.boost(
            chatId = chatId,
            messageId = messageId,
            emoji = emoji,
            plan = plan,
            token = token,
            source = owner.withTimelockForToken(token),
        ).onFailure { error ->
            when (error) {
                is ComputeVerifiedFiatError.AmountBelowMinimum ->
                    alert(R.string.error_title_amountTooSmall, R.string.error_description_amountTooSmall)
                is ComputeVerifiedFiatError ->
                    alert(R.string.error_title_staleRates, R.string.error_description_staleRates)
                else -> alertPaymentFailed()
            }
        }
    }

    /**
     * The alert for a boost that can't go out for a reason the user can't act on. Without it the
     * slider springs back with no explanation.
     */
    fun alertPaymentFailed() {
        alert(R.string.error_title_paymentFailed, R.string.error_description_paymentFailed)
    }

    private fun failed(reason: String): Result<Unit> {
        trace(tag = TAG, message = "Boost not sent: $reason", type = TraceType.Error)
        alertPaymentFailed()
        return blocked()
    }

    private fun alert(title: Int, message: Int) {
        BottomBarManager.showAlert(title = resources.getString(title), message = resources.getString(message))
    }

    private fun blocked(): Result<Unit> = Result.failure(StartChattingPayer.PaymentBlocked())

    private companion object {
        const val TAG = "ReactionBoost"
    }
}
