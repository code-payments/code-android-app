package com.flipcash.app.session.internal.delegates

import com.flipcash.app.analytics.Analytics
import com.flipcash.app.analytics.FlipcashAnalyticsService
import com.flipcash.app.core.bill.BillState
import com.flipcash.app.core.bill.Scannable
import com.flipcash.app.core.bill.PaymentValuation
import com.flipcash.app.core.internal.bill.BillController
import com.flipcash.app.core.internal.errors.showNetworkError
import com.flipcash.app.session.BillDeterminationResult
import com.flipcash.app.session.BillOperations
import com.flipcash.app.session.Grabbed
import com.flipcash.app.session.PutInWallet
import com.flipcash.app.session.internal.SessionStateHolder
import com.flipcash.app.session.internal.toast.SessionToastController
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.core.R
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.user.UserManager
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.util.resources.ResourceHelper
import com.getcode.util.vibration.Vibrator
import com.getcode.utils.ErrorUtils
import com.getcode.utils.TraceType
import com.getcode.utils.network.NetworkConnectivityListener
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implements [BillOperations] — creating, presenting, and dismissing cash bills.
 *
 * This delegate handles the full lifecycle of a bill that the user "gives":
 * 1. Validates the amount and network connectivity.
 * 2. Configures bill actions (Send-as-Link, Cancel).
 * 3. Starts the "await grab" flow that renders the Kik Code and waits for a scan.
 * 4. On grab: subtracts the token balance, enqueues a toast, and emits [Event.RefreshFeed].
 * 5. On Send-as-Link tap: cancels the grab and emits [Event.SendAsLinkRequested] so
 *    the shell can route to [GiftCardSharingDelegate].
 *
 * Cross-delegate communication is handled via [events]; the [com.flipcash.app.session.internal.RealSessionController]
 * shell collects these and dispatches to the appropriate delegate.
 *
 * @see com.flipcash.app.session.internal.RealSessionController
 */
@Singleton
class BillPresentationDelegate @Inject constructor(
    private val billController: BillController,
    private val stateHolder: SessionStateHolder,
    private val toastController: SessionToastController,
    private val tokenCoordinator: TokenCoordinator,
    private val analytics: FlipcashAnalyticsService,
    private val vibrator: Vibrator,
    private val resources: ResourceHelper,
    private val networkObserver: NetworkConnectivityListener,
    private val userManager: UserManager,
    dispatchers: DispatcherProvider,
) : BillOperations {

    sealed interface Event {
        data class SendAsLinkRequested(val bill: Scannable.Payable, val owner: AccountCluster) : Event
        data object RefreshFeed : Event
    }

    private val scope = CoroutineScope(dispatchers.IO + SupervisorJob())

    private val _events = Channel<Event>(Channel.UNLIMITED)
    val events: Flow<Event> = _events.consumeAsFlow()

    override val billState: StateFlow<BillState> get() = billController.state

    override fun showBill(bill: Scannable.Payable) {
        if (bill.amount.nativeAmount.decimalValue == 0.0) return
        val owner = userManager.accountCluster ?: return

        if (!networkObserver.isConnected) {
            return ErrorUtils.showNetworkError(resources)
        }

        when (bill.kind) {
            Scannable.Payable.Kind.airdrop -> {
                billController.update { it.copy(primaryAction = null, secondaryAction = null) }
            }
            Scannable.Payable.Kind.cash -> {
                if (bill.didReceive) {
                    billController.update { it.copy(primaryAction = null, secondaryAction = null) }
                } else {
                    billController.update {
                        it.copy(
                            primaryAction = BillState.Action.SendAsLink(
                                action = {
                                    billController.cancelAwaitForGrab()
                                    _events.trySend(Event.SendAsLinkRequested(bill, owner))
                                }
                            ),
                            secondaryAction = BillState.Action.Cancel(
                                action = { dismissBill(PutInWallet) }
                            ),
                        )
                    }
                }
                awaitBillGrab(bill, owner)
            }
        }
    }

    override fun dismissBill(action: BillDeterminationResult) {
        scope.launch {
            stateHolder.update { it.copy(billResult = action) }
            billController.reset()
            toastController.consumeQueue()
        }
    }

    internal fun awaitBillGrab(bill: Scannable.Payable, owner: AccountCluster) {
        analytics.transferStart(Analytics.Transfer.Initiate.GiveBillStart)
        billController.awaitGrab(
            amount = bill.amount,
            token = bill.token,
            verifiedState = bill.verifiedState,
            nonce = bill.nonce.takeIf { it.isNotEmpty() },
            owner = owner,
            onGrabbed = { amount ->
                tokenCoordinator.subtract(bill.token, amount)
                analytics.transfer(Analytics.Transfer.GiveBill, bill.amount)
                toastController.enqueue(bill.amount, isDeposit = false)
                dismissBill(Grabbed)
                vibrator.vibrate()
                _events.trySend(Event.RefreshFeed)
            },
            onTimeout = {
                dismissBill(action = PutInWallet)
            },
            onError = {
                analytics.transfer(
                    event = Analytics.Transfer.GiveBill,
                    amount = bill.amount,
                    successful = false,
                    error = it
                )
                dismissBill(action = PutInWallet)
                BottomBarManager.showError(
                    title = resources.getString(R.string.error_title_CashReturnedToWallet),
                    message = resources.getString(R.string.error_description_CashReturnedToWallet)
                )
            },
            present = { (data, nonce) ->
                if (!bill.didReceive) {
                    trace(
                        tag = "Session",
                        message = "Pull out cash",
                        metadata = {
                            "underlying quarks" to bill.amount.underlyingTokenAmount.quarks
                            "native amount" to bill.amount.nativeAmount.formatted()
                            "fx" to bill.amount.rate.fx
                            "currency" to bill.amount.rate.currency.name
                            "token mint" to bill.amount.mint
                        },
                        type = TraceType.User,
                    )
                }
                presentBillToUser(data, nonce, bill)
            },
        )
    }

    private fun presentBillToUser(data: List<Byte>, nonce: List<Byte>, bill: Scannable.Payable) {
        if (billController.state.value.bill != null) return

        val presentedBill = bill.stamped(code = data, nonce = nonce)

        billController.update {
            it.copy(
                bill = presentedBill,
                valuation = PaymentValuation(bill.amount.nativeAmount),
            )
        }

        val style: BillDeterminationResult =
            if (bill.didReceive) Grabbed else PutInWallet

        stateHolder.update { it.copy(billResult = style) }

        if (bill.didReceive) {
            toastController.enqueue(bill.amount, isDeposit = true)
            vibrator.vibrate(duration = 50)
        }
    }
}
