package com.flipcash.app.messenger.internal.payment

import androidx.annotation.StringRes
import com.flipcash.analytics.AddMoneySource
import com.flipcash.analytics.Amount
import com.flipcash.analytics.SendContext
import com.flipcash.analytics.State as AnalyticsState
import com.flipcash.analytics.events.AddMoneyEvents
import com.flipcash.analytics.events.TransferEvents
import com.flipcash.app.analytics.FlipcashAnalytics
import com.flipcash.app.analytics.analytics
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.chat.ProfileOrigin
import com.flipcash.app.funding.PurchaseMethodController
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.features.messenger.R
import com.flipcash.services.models.TipAction
import com.flipcash.services.models.TipOrigin
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.user.UserManager
import com.flipcash.shared.payments.TipPaymentDelegate
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.exchange.VerifiedFiatCalculator
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.core.errors.ComputeVerifiedFiatError
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Token
import com.getcode.util.resources.ResourceHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * The money side of paying a recipient's fee to start a chat: the gate before anything is offered,
 * and the payment itself. Lifted out of `ChatViewModel` so the chat and the other-user profile
 * share one copy of the alerts and the checks.
 *
 * Nothing here navigates. An alert's action calls back into the caller, which owns where "Add
 * Money" and "Discover Currencies" lead.
 */
internal interface StartChattingPayer {

    /** The token the sender's payment would come from. */
    fun observeSelectedToken(): Flow<Token>

    /**
     * Whether the sender holds anything to pay with. When not, an alert has been raised offering
     * the way out and the caller should stop: [onAddMoney] for an empty account,
     * [onDiscoverCurrencies] for one that only holds reserves.
     */
    suspend fun mayProceed(onAddMoney: () -> Unit, onDiscoverCurrencies: () -> Unit): Boolean

    /** Over balance, with something in the account. [onAddMoney] is the alert's action. */
    fun presentInsufficientBalance(
        onAddMoney: () -> Unit,
        @StringRes message: Int = R.string.description_insufficientBalanceToUse,
    )

    /** The deposit screen to open for an "Add Money" action, or null when there is none to open. */
    suspend fun depositRoute(): AppRoute?

    /**
     * Pays [fee] to [recipient] from the selected token, the payment that opens the DM with them.
     *
     * [origin] is the profile the payment was made from, and decides what the payment is: a tip
     * when the profile came from a Flipcard ([ProfileOrigin.Scan], a scan or a tip-card link), a
     * send from anywhere else. The server stores that action on the cash message, so it is what
     * the DM's first bubble reads as on both ends.
     *
     * A failure that is a [PaymentBlocked] has already told the user why (balance, limit, stale
     * rates); any other failure has not, and is the caller's to present.
     */
    suspend fun pay(
        recipient: ID,
        fee: Fiat,
        origin: ProfileOrigin,
        onAddMoney: () -> Unit,
    ): Result<ChatId?>

    /** A payment that was stopped by a check, after the user was told. Not worth a second message. */
    class PaymentBlocked : Exception()
}

internal class DefaultStartChattingPayer @Inject constructor(
    private val tokenCoordinator: TokenCoordinator,
    private val exchange: Exchange,
    private val userManager: UserManager,
    private val verifiedFiatCalculator: VerifiedFiatCalculator,
    private val tipPaymentDelegate: TipPaymentDelegate,
    private val purchaseMethodController: PurchaseMethodController,
    private val analytics: FlipcashAnalytics,
    private val resources: ResourceHelper,
) : StartChattingPayer {

    override fun observeSelectedToken(): Flow<Token> =
        tokenCoordinator.observeSelectedTokenMint()
            .flatMapLatest { mint ->
                tokenCoordinator.tokenBalances.map { tokens ->
                    tokens.find { it.token.address == mint }?.token
                }
            }
            .filterNotNull()

    override suspend fun mayProceed(
        onAddMoney: () -> Unit,
        onDiscoverCurrencies: () -> Unit,
    ): Boolean {
        if (tokenCoordinator.hasGiveableBalance()) return true
        if (!tokenCoordinator.hasBalance()) {
            presentAddMoney(onAddMoney)
        } else {
            presentDiscoverCurrencies(onDiscoverCurrencies)
        }
        return false
    }

    override fun presentInsufficientBalance(onAddMoney: () -> Unit, @StringRes message: Int) {
        BottomBarManager.showInfo(
            title = resources.getString(R.string.title_insufficientBalance),
            message = resources.getString(message),
            actions = listOf(
                BottomBarAction(text = resources.getString(R.string.action_addMoney)) { onAddMoney() },
            ),
            showCancel = true,
        )
    }

    override suspend fun depositRoute(): AppRoute? {
        analytics.track(AddMoneyEvents.opened(AddMoneySource.CHAT))
        return purchaseMethodController.presentDepositOptions()
    }

    override suspend fun pay(
        recipient: ID,
        fee: Fiat,
        origin: ProfileOrigin,
        onAddMoney: () -> Unit,
    ): Result<ChatId?> {
        val owner = userManager.accountCluster
            ?: return Result.failure(IllegalStateException("no account"))
        val token = observeSelectedToken().first()
        val rate = exchange.preferredRate
        val balance = tokenCoordinator.balanceForToken(token)

        if (fee.valueGreaterThan(balance.convertingTo(rate))) {
            presentInsufficientBalance(onAddMoney)
            return Result.failure(StartChattingPayer.PaymentBlocked())
        }
        if (tipPaymentDelegate.exceedsSendLimit(fee)) {
            BottomBarManager.showAlert(
                resources.getString(R.string.error_title_sendLimitReached),
                resources.getString(R.string.error_description_sendLimitReached),
            )
            return Result.failure(StartChattingPayer.PaymentBlocked())
        }

        val verifiedFiat = verifiedFiatCalculator.compute(
            amount = fee,
            token = token,
            balance = balance,
            rate = rate,
        ).getOrElse { error ->
            val (title, message) = when (error) {
                is ComputeVerifiedFiatError.AmountBelowMinimum ->
                    R.string.error_title_amountTooSmall to R.string.error_description_amountTooSmall
                else -> R.string.error_title_staleRates to R.string.error_description_staleRates
            }
            BottomBarManager.showAlert(
                title = resources.getString(title),
                message = resources.getString(message),
            )
            return Result.failure(StartChattingPayer.PaymentBlocked())
        }

        val action = if (origin == ProfileOrigin.Scan) TipAction.TIP else TipAction.SEND
        val result = tipPaymentDelegate.send(
            userId = recipient,
            verifiedFiat = verifiedFiat,
            token = token,
            source = owner.withTimelockForToken(token),
            // FLIPCARD on the wire for every opener: the server is known to accept SEND with it, and
            // CHAT on an opening payment is unconfirmed.
            origin = TipOrigin.TIPCARD,
            action = action,
        )
        val sentAmount = verifiedFiat.localFiat.analytics
        val sent = { state: AnalyticsState, amount: Amount, error: String? ->
            when (action) {
                TipAction.TIP -> TransferEvents.sentTip(state, amount, error)
                TipAction.SEND -> TransferEvents.sentCash(state, amount, error, SendContext.DIRECT, null)
            }
        }
        analytics.track(
            result.fold(
                onSuccess = { sent(AnalyticsState.SUCCESS, sentAmount, null) },
                onFailure = { sent(AnalyticsState.FAILURE, sentAmount, it.analytics) },
            )
        )
        return result
    }

    private fun presentAddMoney(onAddMoney: () -> Unit) {
        BottomBarManager.showInfo(
            title = resources.getString(R.string.title_noBalanceYet),
            message = resources.getString(R.string.description_noBalanceYetToSend),
            actions = listOf(
                BottomBarAction(text = resources.getString(R.string.action_addMoney)) { onAddMoney() },
            ),
            showCancel = true,
        )
    }

    private fun presentDiscoverCurrencies(onDiscover: () -> Unit) {
        BottomBarManager.showInfo(
            title = resources.getString(R.string.title_noCommunityCurrenciesYet),
            message = resources.getString(R.string.description_noCommunityCurrenciesYet),
            actions = listOf(
                BottomBarAction(text = resources.getString(R.string.action_discoverCurrencies)) {
                    onDiscover()
                },
            ),
            showCancel = true,
        )
    }
}
