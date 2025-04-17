package com.flipcash.app.core.internal.session

import com.flipcash.app.core.PresentationStyle
import com.flipcash.app.core.SessionController
import com.flipcash.app.core.SessionState
import com.flipcash.app.core.bill.Bill
import com.flipcash.app.core.bill.BillController
import com.flipcash.app.core.bill.BillState
import com.flipcash.app.core.bill.BillToast
import com.flipcash.app.core.bill.DeepLinkRequest
import com.flipcash.app.core.bill.PaymentValuation
import com.flipcash.app.core.internal.errors.showNetworkError
import com.flipcash.core.R
import com.flipcash.services.controllers.AccountController
import com.flipcash.services.controllers.ActivityFeedController
import com.flipcash.services.models.ActivityFeedType
import com.flipcash.services.user.AuthState
import com.flipcash.services.user.UserManager
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.model.core.OpenCodePayload
import com.getcode.opencode.model.core.PayloadKind
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.transactions.AirdropType
import com.getcode.ui.core.RestrictionType
import com.getcode.util.permissions.PermissionResult
import com.getcode.util.resources.ResourceHelper
import com.getcode.util.vibration.Vibrator
import com.getcode.utils.ErrorUtils
import com.getcode.utils.TraceType
import com.getcode.utils.base58
import com.getcode.utils.hexEncodedString
import com.getcode.utils.network.NetworkConnectivityListener
import com.getcode.utils.trace
import com.kik.kikx.models.ScannableKikCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class RealSessionController @Inject constructor(
    private val billController: BillController,
    private val userManager: UserManager,
    private val accountController: AccountController,
    private val activityFeedController: ActivityFeedController,
    private val transactionController: TransactionController,
    private val networkObserver: NetworkConnectivityListener,
    private val resources: ResourceHelper,
    private val vibrator: Vibrator
) : SessionController {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _state = MutableStateFlow(SessionState())

    override val state: StateFlow<SessionState>
        get() = _state.asStateFlow()

    override val billState: StateFlow<BillState>
        get() = billController.state

    private val scannedRendezvous = mutableListOf<String>()

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
        populateActivityFeed()
    }

    override fun onAppInBackground() {
        billController.reset()
    }

    private fun updateUserFlags() {
        scope.launch {
            accountController.getUserFlags()
        }
    }

    private fun requestAirdrop() {
        scope.launch {
            userManager.accountCluster?.let {
                transactionController.airdrop(
                    type = AirdropType.GetFirstCrypto,
                    destination = it.authority.keyPair
                ).onSuccess { amount ->
                    showToast(amount = amount, isDeposit = true, initialDelay = 1.seconds)
                }
            }
        }
    }

    private fun populateActivityFeed() {
        scope.launch {
            activityFeedController.getLatestMessagesFor(ActivityFeedType.TransactionHistory)
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

        // Don't show the remote send and cancel buttons for airdrop
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
                    if (bill.didReceive) {
                        billController.update {
                            it.copy(
                                primaryAction = null,
                                secondaryAction = null,
                            )
                        }
                    } else {
                        billController.update {
                            it.copy(
                                primaryAction = BillState.Action.Cancel(
                                    label = resources.getString(R.string.action_cancel),
                                    action = { cancelSend() }
                                ),
                                secondaryAction = null,
                            )
                        }
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
                    activityFeedController.refreshAfterEvent(ActivityFeedType.TransactionHistory)
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

    override fun onRemoteSend() {
        TODO("Not yet implemented")
    }

    override fun onCodeScan(code: ScannableKikCode) {
        if (billController.state.value.bill != null) {
            trace(
                tag = "Session",
                message = "Already showing a bill",
                type = TraceType.Silent
            )
            return
        }

        val payload = (code as? ScannableKikCode.RemoteKikCode)?.payloadId?.toList() ?: return
        val codePayload = OpenCodePayload.fromList(payload)
        if (scannedRendezvous.contains(codePayload.rendezvous.publicKey)) {
            trace(
                tag = "Session",
                message = "Nonce previously received: ${codePayload.nonce.hexEncodedString()}",
                type = TraceType.Silent
            )
            return
        }

        scannedRendezvous.add(codePayload.rendezvous.publicKey)

        trace(
            tag = "Session",
            message = """
                Kind: ${codePayload.kind}
                Nonce: ${codePayload.nonce.hexEncodedString()}
                Rendezvous: ${codePayload.rendezvous.publicKeyBytes.base58}
            """.trimIndent()
        )

        when (codePayload.kind) {
            PayloadKind.Cash -> onCashScanned(codePayload)
        }
    }

    override fun handleRequest(request: DeepLinkRequest?) {
        TODO("Not yet implemented")
    }

    private fun onCashScanned(payload: OpenCodePayload) {
        trace(
            tag = "Session",
            message = "Scanned: ${payload.fiat!!.formatted()} ${payload.fiat!!.currencyCode}"
        )
        val owner = userManager.accountCluster ?: return

        billController.attemptGrab(
            owner = owner,
            payload = payload,
            onGrabbed = { amount ->
                BottomBarManager.clear()
                showBill(
                    bill = Bill.Cash(amount = amount, didReceive = true),
                    vibrate = true
                )
                scope.launch {
                    activityFeedController.refreshAfterEvent(ActivityFeedType.TransactionHistory)
                }
            },
            onError = {
                scannedRendezvous.remove(payload.rendezvous.publicKey)
                ErrorUtils.handleError(it)
            }
        )
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

    override fun cancelSend(style: PresentationStyle) {
        BottomBarManager.clearByType(BottomBarManager.BottomBarMessageType.REMOTE_SEND)

        scope.launch {
            val shown = showToastIfNeeded(style)
            _state.update { it.copy(presentationStyle = style) }
            billController.reset(showToast = shown)

            if (shown) {
                delay(5.seconds)
            }
            billController.reset()
        }
    }

    private fun showToastIfNeeded(
        style: PresentationStyle,
    ): Boolean {
        val billState = billController.state.value
        val bill = billState.bill ?: return false

        if (style is PresentationStyle.Pop || billState.showToast) {
            showToast(
                amount = bill.metadata.amount,
                isDeposit = when (style) {
                    PresentationStyle.Slide -> true
                    PresentationStyle.Pop -> false
                    else -> false
                },
            )

            return true
        }

        return false
    }

    private fun showToast(
        amount: LocalFiat,
        isDeposit: Boolean = false,
        initialDelay: Duration = 500.milliseconds
    ) {
        if (amount.converted.doubleValue == 0.0) {
            return
        }

        scope.launch {
            delay(initialDelay)
            billController.update {
                it.copy(
                    showToast = true,
                    toast = BillToast(amount = amount.converted, isDeposit = isDeposit)
                )
            }

            delay(5.seconds)

            billController.update {
                it.copy(
                    showToast = false
                )
            }

            // wait for animation to run
            delay(500.milliseconds)
            billController.update {
                it.copy(toast = null)
            }
        }
    }
}