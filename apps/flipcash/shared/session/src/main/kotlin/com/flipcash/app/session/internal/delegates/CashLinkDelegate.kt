package com.flipcash.app.session.internal.delegates

import com.flipcash.app.analytics.Analytics
import com.flipcash.app.analytics.FlipcashAnalyticsService
import com.flipcash.app.core.bill.Bill
import com.flipcash.app.core.internal.bill.BillController
import com.flipcash.app.core.navigation.DeeplinkType
import com.flipcash.app.session.CashLinkOperations
import com.flipcash.app.session.internal.SessionStateHolder
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.core.R
import com.flipcash.services.user.UserManager
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.internal.transactors.ReceiveGiftTransactorError
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implements [CashLinkOperations] — opening (claiming) cash links.
 *
 * 1. Validates the entropy string (null, empty, whitespace).
 * 2. Guards against duplicate concurrent claims via [giftCardClaimInProgress].
 * 3. Calls [BillController.receiveGiftCard] to claim the gift card.
 * 4. On success: adds the token to the balance and emits [Event.BillReady],
 *    [Event.CheckPendingFeed], and [Event.RefreshFeed] for the shell to route.
 * 5. On error: shows the appropriate error dialog (already claimed, expired,
 *    user's own gift card with a "collect anyway?" prompt, or generic error).
 *
 * @see com.flipcash.app.session.internal.RealSessionController
 */
@Singleton
class CashLinkDelegate @Inject constructor(
    private val stateHolder: SessionStateHolder,
    private val billController: BillController,
    private val tokenCoordinator: TokenCoordinator,
    private val analytics: FlipcashAnalyticsService,
    private val resources: ResourceHelper,
    private val userManager: UserManager,
) : CashLinkOperations {

    sealed interface Event {
        data class BillReady(val bill: Bill) : Event
        data object RefreshFeed : Event
        data object CheckPendingFeed : Event
    }

    private val _events = Channel<Event>(Channel.UNLIMITED)
    val events: Flow<Event> = _events.consumeAsFlow()

    private val giftCardClaimInProgress = MutableStateFlow<String?>(null)

    override fun openCashLink(cashLink: String?) {
        BottomBarManager.clear()

        val entropy = cashLink?.trim()?.replace("\n", "")
        if (entropy == null) {
            trace(
                tag = "Session",
                message = "Cash link not provided",
                type = TraceType.Silent
            )
            analytics.deeplinkRouted(
                DeeplinkType.CashLink(),
                error = IllegalArgumentException("Cash link not provided")
            )
            return
        }
        val owner = userManager.accountCluster

        if (owner == null) {
            trace(
                tag = "Session",
                message = "No owner found",
                type = TraceType.Silent
            )
            analytics.deeplinkRouted(
                DeeplinkType.CashLink(),
                error = IllegalStateException("No owner found")
            )
            return
        }

        if (entropy.isEmpty()) {
            trace(
                tag = "Session",
                message = "Cash link empty",
                type = TraceType.Silent
            )
            analytics.deeplinkRouted(
                DeeplinkType.CashLink(),
                error = IllegalArgumentException("Cash link empty")
            )
            return
        }

        if (giftCardClaimInProgress.value == null) {
            giftCardClaimInProgress.value = entropy
            analytics.deeplinkRouted(DeeplinkType.CashLink())
            claimGiftCard(owner = owner, entropy = entropy, claimIfOwned = false)
        }
    }

    private fun claimGiftCard(
        owner: AccountCluster,
        entropy: String,
        claimIfOwned: Boolean
    ) {
        trace(
            tag = "Session",
            message = "Claiming gift card: $entropy",
            type = TraceType.Silent
        )

        billController.receiveGiftCard(
            entropy = entropy,
            owner = owner,
            claimIfOwned = claimIfOwned,
            onReceived = { token, amount ->
                tokenCoordinator.add(token, amount)
                giftCardClaimInProgress.value = null
                analytics.transfer(Analytics.Transfer.ClaimedCashLink, amount = amount)
                val bill = Bill.Cash(
                    amount = amount,
                    token = token,
                    didReceive = true,
                )
                _events.trySend(Event.BillReady(bill))
                _events.trySend(Event.CheckPendingFeed)
                _events.trySend(Event.RefreshFeed)
            },
            onError = { cause ->
                giftCardClaimInProgress.value = null
                if (cause !is ReceiveGiftTransactorError.UsersGiftCard) {
                    analytics.transfer(
                        Analytics.Transfer.ClaimedCashLink,
                        amount = null,
                        successful = false,
                        error = cause
                    )
                }

                when (cause) {
                    is ReceiveGiftTransactorError.UsersGiftCard -> {
                        BottomBarManager.showAlert(
                            title = resources.getString(R.string.prompt_title_collectOwnCash),
                            message = resources.getString(R.string.prompt_description_collectOwnCash),
                            isDismissible = false,
                            showScrim = false,
                            actions = buildList {
                                add(
                                    BottomBarAction(
                                        text = resources.getString(R.string.action_dontCollect),
                                    )
                                )

                                add(
                                    BottomBarAction(
                                        text = resources.getString(R.string.action_collect),
                                        style = BottomBarManager.BottomBarButtonStyle.Text,
                                        onClick = {
                                            claimGiftCard(
                                                owner = owner,
                                                entropy = entropy,
                                                claimIfOwned = true
                                            )
                                        }
                                    )
                                )
                            }
                        )
                    }

                    is ReceiveGiftTransactorError.AlreadyClaimed -> {
                        BottomBarManager.showAlert(
                            resources.getString(R.string.error_title_alreadyCollected),
                            resources.getString(R.string.error_description_alreadyCollected)
                        )
                    }

                    is ReceiveGiftTransactorError.Expired -> {
                        BottomBarManager.showAlert(
                            resources.getString(R.string.error_title_linkExpired),
                            resources.getString(R.string.error_description_linkExpired)
                        )
                    }

                    else -> {
                        BottomBarManager.showError(
                            resources.getString(R.string.error_title_failedToCollect),
                            resources.getString(R.string.error_description_failedToCollect)
                        )
                    }
                }
            }
        )
    }
}
