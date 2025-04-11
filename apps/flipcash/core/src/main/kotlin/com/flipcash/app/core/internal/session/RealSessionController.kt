package com.flipcash.app.core.internal.session

import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.PresentationStyle
import com.flipcash.app.core.SessionController
import com.flipcash.app.core.SessionState
import com.flipcash.app.core.bill.Bill
import com.flipcash.app.core.bill.BillController
import com.flipcash.app.core.bill.BillState
import com.flipcash.app.core.bill.DeepLinkRequest
import com.flipcash.app.core.bill.PaymentValuation
import com.flipcash.app.core.internal.errors.showNetworkError
import com.flipcash.services.controllers.AccountController
import com.flipcash.services.user.AuthState
import com.flipcash.services.user.UserManager
import com.getcode.opencode.controllers.BalanceController
import com.getcode.opencode.model.transactions.AirdropType
import com.getcode.ui.core.RestrictionType
import com.getcode.util.permissions.PermissionResult
import com.getcode.util.resources.ResourceHelper
import com.getcode.util.vibration.Vibrator
import com.getcode.utils.ErrorUtils
import com.getcode.utils.TraceType
import com.getcode.utils.network.NetworkConnectivityListener
import com.getcode.utils.trace
import com.kik.kikx.models.ScannableKikCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

class RealSessionController @Inject constructor(
    private val billController: BillController,
    private val userManager: UserManager,
    private val accountController: AccountController,
    private val balanceController: BalanceController,
    private val networkObserver: NetworkConnectivityListener,
    private val resources: ResourceHelper,
    private val vibrator: Vibrator
): SessionController {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _state = MutableStateFlow(SessionState())

    override val state: StateFlow<SessionState>
        get() = _state.asStateFlow()

    override val billState: StateFlow<BillState>
        get() = billController.state

    init {
        userManager.state
            .map { it.isTimelockUnlocked }
            .onEach { _state.update { it.copy(restrictionType = RestrictionType.TIMELOCK_UNLOCKED) } }
            .launchIn(scope)

        userManager.state
            .mapNotNull { it.authState }
            .filterIsInstance<AuthState.LoggedIn>()
            .distinctUntilChanged()
            .onEach { onAppInForeground() }
            .launchIn(scope)
    }

    override fun onAppInForeground() {
        updateUserFlags()
        requestAirdrop()
    }

    private fun updateUserFlags() {
        scope.launch {
            accountController.getUserFlags()
        }
    }

    private fun requestAirdrop() {
        scope.launch {
            userManager.accountCluster?.let {
                balanceController.airdrop(
                    type = AirdropType.GetFirstCrypto,
                    destination = it.authority.keyPair
                )
            }
        }
    }

    override fun onCameraScanning(scanning: Boolean) {
        _state.update { it.copy(isCameraScanEnabled = scanning) }
    }

    override fun onCameraPermissionResult(result: PermissionResult) {
        _state.update { it.copy(isCameraPermissionGranted = result == PermissionResult.Granted) }
    }

    override fun showBill(bill: Bill, vibrate: Boolean) {
        if (bill.amount.converted.doubleValue == 0.0) return
        val owner = userManager.accountCluster ?: return

        if (!networkObserver.isConnected) {
            return ErrorUtils.showNetworkError(resources)
        }

        // Don't show the remote send and cancel buttons for first kin
        when (bill) {
            is Bill.Cash -> {
                if (bill.kind == Bill.Kind.airdrop) {
                    billController.update {
                        it.copy(
                            primaryAction = null,
                            secondaryAction = null,
                        )
                    }
                } else {
                    billController.update {
                        it.copy(
                            primaryAction = BillState.Action.Send { onRemoteSend() },
                            secondaryAction = BillState.Action.Cancel(::cancelSend)
                        )
                    }
                }
            }

            else -> Unit
        }

        billController.awaitGrab(
            amount = bill.amount,
            owner = owner,
            onGrabbed = {
                cancelSend(PresentationStyle.Pop)
                vibrator.vibrate()

                scope.launch {
//                    client.fetchLimits(true).subscribe({}, ErrorUtils::handleError)
                }
            },
            onTimeout = {
                cancelSend(style = PresentationStyle.Slide)
//                analytics.billTimeoutReached(
//                    bill.amount.kin,
//                    bill.amount.rate.currency,
//                    CodeAnalyticsManager.BillPresentationStyle.Slide
//                )
            },
            onError = { cancelSend(style = PresentationStyle.Slide) },
            present = { data ->
                if (!bill.didReceive) {
                    trace(
                        tag = "Bill",
                        message = "Pull out cash",
                        metadata = {
                            "amount" to bill.amount
                        },
                        type = TraceType.User,
                    )
                }
                presentSend(data, bill, vibrate)
            },
        )
    }

    override fun cancelSend(style: PresentationStyle) {
        billController.reset()
    }

    override fun onRemoteSend() {
        TODO("Not yet implemented")
    }

    override fun onCodeScan(code: ScannableKikCode) {
        TODO("Not yet implemented")
    }

    override fun handleRequest(request: DeepLinkRequest?) {
        TODO("Not yet implemented")
    }

    private fun presentSend(data: List<Byte>, bill: Bill, isVibrate: Boolean = false) {
        if (bill.didReceive) {
            billController.update {
                it.copy(
                    valuation = PaymentValuation(bill.amount.converted),
                )
            }
        }

        val style: PresentationStyle =
            if (bill.didReceive) PresentationStyle.Pop else PresentationStyle.Slide

        _state.update { it.copy(presentationStyle = style) }
        billController.update {
            it.copy(
                bill = Bill.Cash(
                    data = data,
                    amount = bill.amount,
                    didReceive = bill.didReceive
                ),
                valuation = PaymentValuation(bill.amount.converted),
                showToast = bill.didReceive
            )
        }

        if (style is PresentationStyle.Visible) {
//            analytics.billShown(
//                bill.amountFloored.kin,
//                bill.amountFloored.rate.currency,
//                when (style) {
//                    PresentationStyle.Pop -> CodeAnalyticsManager.BillPresentationStyle.Pop
//                    PresentationStyle.Slide -> CodeAnalyticsManager.BillPresentationStyle.Slide
//                }
//            )
        }

        if (isVibrate) {
            vibrator.vibrate()
        }
    }
}