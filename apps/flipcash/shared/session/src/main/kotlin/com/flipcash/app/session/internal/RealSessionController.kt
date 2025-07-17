package com.flipcash.app.session.internal

import com.flipcash.app.activityfeed.ActivityFeedCoordinator
import com.flipcash.app.activityfeed.ActivityFeedUpdater
import com.flipcash.app.appsettings.AppSettingValue
import com.flipcash.app.appsettings.AppSettingsCoordinator
import com.flipcash.app.billing.BillingClient
import com.flipcash.app.core.bill.Bill
import com.flipcash.app.core.bill.BillState
import com.flipcash.app.core.bill.PaymentValuation
import com.flipcash.app.core.internal.bill.BillController
import com.flipcash.app.core.internal.errors.showNetworkError
import com.flipcash.app.core.internal.updater.BalanceUpdater
import com.flipcash.app.core.internal.updater.ExchangeUpdater
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.pools.PoolUpdater
import com.flipcash.app.pools.PoolsCoordinator
import com.flipcash.app.pools.PoolsUpdater
import com.flipcash.app.session.BillDeterminationResult
import com.flipcash.app.session.Grabbed
import com.flipcash.app.session.PutInWallet
import com.flipcash.app.session.SessionController
import com.flipcash.app.session.SessionState
import com.flipcash.app.session.internal.toast.ToastController
import com.flipcash.app.shareable.ShareConfirmationResult
import com.flipcash.app.shareable.ShareResult
import com.flipcash.app.shareable.ShareSheetController
import com.flipcash.app.shareable.Shareable
import com.flipcash.app.shareable.ShareableConfirmationController
import com.flipcash.core.R
import com.flipcash.services.analytics.AnalyticsEvent
import com.flipcash.services.analytics.FlipcashAnalyticsService
import com.flipcash.services.controllers.AccountController
import com.flipcash.services.user.AuthState
import com.flipcash.services.user.UserManager
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.BalanceController
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.internal.transactors.ReceiveGiftTransactorError
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.GiftCardAccount
import com.getcode.opencode.model.core.OpenCodePayload
import com.getcode.opencode.model.core.PayloadKind
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.transactions.AirdropType
import com.getcode.opencode.utils.nonce
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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class RealSessionController @Inject constructor(
    private val billController: BillController,
    private val userManager: UserManager,
    private val accountController: AccountController,
    private val feedCoordinator: ActivityFeedCoordinator,
    private val poolsCoordinator: PoolsCoordinator,
    private val transactionController: TransactionController,
    private val networkObserver: NetworkConnectivityListener,
    private val resources: ResourceHelper,
    private val vibrator: Vibrator,
    private val balanceUpdater: BalanceUpdater,
    private val exchangeUpdater: ExchangeUpdater,
    private val activityFeedUpdater: ActivityFeedUpdater,
    private val poolsUpdater: PoolsUpdater,
    private val poolUpdater: PoolUpdater,
    private val shareSheetController: ShareSheetController,
    private val shareConfirmationController: ShareableConfirmationController,
    private val toastController: ToastController,
    private val billingClient: BillingClient,
    private val balanceController: BalanceController,
    private val featureFlagController: FeatureFlagController,
    private val analytics: FlipcashAnalyticsService,
    appSettingsCoordinator: AppSettingsCoordinator,
) : SessionController {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _state = MutableStateFlow(SessionState())

    override val state: StateFlow<SessionState>
        get() = _state.asStateFlow()

    override val billState: StateFlow<BillState>
        get() = billController.state

    private val scannedRendezvous = mutableListOf<String>()

    init {
        // reset state on logouts
        userManager.state
            .map { it.authState }
            .filterIsInstance<AuthState.LoggedOut>()
            .onEach {
                _state.update { SessionState() }
            }.launchIn(scope)

        userManager.state
            .map { it.isTimelockUnlocked }
            .onEach { _state.update { it.copy(restrictionType = RestrictionType.TIMELOCK_UNLOCKED) } }
            .launchIn(scope)

        userManager.state
            .mapNotNull { it.authState }
            .filter { it.canAccessAuthenticatedApis }
            .distinctUntilChanged()
            .onEach { onAppInForeground() }
            .launchIn(scope)

        userManager.state
            .mapNotNull { it.authState }
            .filter { it.isAtLeastRegistered }
            .distinctUntilChanged()
            .onEach { billingClient.connect() }
            .launchIn(scope)

        appSettingsCoordinator
            .observeValue(AppSettingValue.CameraStartByDefault)
            .onEach { autoStart -> _state.update { it.copy(autoStartCamera = autoStart) } }
            .launchIn(scope)

        featureFlagController.observe(FeatureFlag.VibrateOnScan)
            .onEach { enabled -> _state.update { it.copy(vibrateOnScan = enabled) } }
            .launchIn(scope)

        featureFlagController.observe(FeatureFlag.Pools)
            .onEach { enabled -> _state.update { it.copy(poolsOpen = enabled) } }
            .launchIn(scope)

        poolsCoordinator.openPool
            .onEach { id ->
                if (id == null) {
                    poolUpdater.stop()
                } else {
                    poolUpdater.poll(
                        key = id,
                        scope = scope,
                        frequency = 10.seconds,
                        startIn = 10.seconds
                    )
                }
            }.launchIn(scope)

        state
            .map { it.isCameraUp }
            .distinctUntilChanged() // Emit only when value changes
            .scan(Pair(null as Boolean?, null as Boolean?)) { previousPair, current ->
                Pair(previousPair.second, current)
            }
            .mapNotNull { (previous, current) ->
                if (previous == null && current != null) current else null
            }
            .onEach { checkForAirdrops() }
            .launchIn(scope)
    }

    /**
     * Called when the app enters the foreground.
     *
     * This function performs several actions to ensure the app is up-to-date and ready for user interaction:
     * 1. Starts polling for updates (e.g., balance, exchange rates, activity feed).
     * 2. Updates user flags.
     * 4. Checks for pending items in the activity feed.
     * 5. Brings the activity feed to the current state.
     * 6. Checks for any pending share actions via the share sheet.
     * 7. If the user is registered, connects to the billing client.
     */
    override fun onAppInForeground() {
        trace(
            tag = "Session",
            message = "onAppInForeground",
            type = TraceType.Process,
        )
        startPolling()
        updateUserFlags()
        checkPendingItemsInFeed()
        bringActivityFeedCurrent()
        bringPoolsCurrent()
        shareSheetController.checkForShare()
        if (userManager.authState.isAtLeastRegistered) {
            billingClient.connect()
        }
    }

    /**
     * Called when the application enters the background.
     * This function stops polling for updates, disconnects the billing client,
     * and clears any pending UI elements related to bills or sharing if certain conditions are met.
     *
     * Specifically, it clears the bottom bar, cancels any pending bill grab actions, and cancels
     * any ongoing send operations if:
     * - The share sheet is not currently checking for a share action, OR
     * - There is an active bill and it has not yet been received.
     */
    override fun onAppInBackground() {
        stopPolling()
        billingClient.disconnect()

        toastController.clear()

        val bill = billController.state.value.bill
        if (!shareSheetController.isCheckingForShare || (bill != null && !bill.didReceive)) {
            BottomBarManager.clear()
            billController.cancelAwaitForGrab()
            dismissBill(PutInWallet)
        }
    }

    private fun startPolling() {
        if (userManager.authState.canAccessAuthenticatedApis) {
            exchangeUpdater.poll(scope = scope, frequency = 10.seconds, startIn = 10.seconds)
            balanceUpdater.poll(scope = scope, frequency = 60.seconds, startIn = 0.seconds)
            activityFeedUpdater.poll(scope = scope, frequency = 60.seconds, startIn = 60.seconds)
            // TODO: once we have streams setup for pool this can be removed
            poolsUpdater.poll(scope = scope, frequency = 60.seconds, startIn = 45.seconds)

            poolsCoordinator.openPool.value?.let { id ->
                poolUpdater.poll(id, scope = scope, frequency = 10.seconds, startIn = 10.seconds)
            }
        }
    }

    private fun stopPolling() {
        exchangeUpdater.stop()
        balanceUpdater.stop()
        activityFeedUpdater.stop()
        // TODO: once we have streams setup for pool this can be removed
        poolsUpdater.stop()
        poolUpdater.stop()
    }

    private fun updateUserFlags() {
        if (userManager.authState.canAccessAuthenticatedApis) {
            scope.launch {
                accountController.getUserFlags()
            }
        }
    }

    private fun checkForAirdrops() {
        if (userManager.authState.canAccessAuthenticatedApis) {
            scope.launch {
                userManager.accountCluster?.let {
                    transactionController.airdrop(
                        type = AirdropType.WelcomeBonus,
                        destination = it.authority.keyPair
                    ).onSuccess { amount ->
                        presentWelcomeBonus(amount)
                    }
                }
            }
        }
    }

    private fun presentWelcomeBonus(amount: LocalFiat) {
        scope.launch {
            val presentWithBill = featureFlagController.get(FeatureFlag.WelcomeBonusBill)
            toastController.enqueue(
                amount = amount,
                isDeposit = true,
                initialDelay = if (presentWithBill) ToastController.INITIAL_DELAY else AIRDROP_INITIAL_DELAY
            )

            if (!presentWithBill) {
                // consume the toast immediately
                toastController.consumeQueue()
                return@launch
            }

            delay(1.seconds)
            val payloadInfo = OpenCodePayload(
                kind = PayloadKind.Cash,
                value = amount.converted,
                nonce = nonce
            )

            val bill = Bill.Cash(
                data = payloadInfo.codeData.toList(),
                amount = amount,
                didReceive = true,
                confirmationDelay = 500.milliseconds
            )

            presentBillToUser(data = payloadInfo.codeData.toList(), bill = bill)
            feedCoordinator.fetchSinceLatest()
        }
    }

    private fun checkPendingItemsInFeed() {
        if (userManager.authState.canAccessAuthenticatedApis) {
            scope.launch {
                feedCoordinator.checkPendingMessagesForUpdates()
            }
        }
    }

    private fun bringActivityFeedCurrent(count: Int = 100) {
        if (userManager.authState.canAccessAuthenticatedApis) {
            scope.launch {
                feedCoordinator.fetchSinceLatest(count)
            }
        }
    }

    private fun bringPoolsCurrent(count: Int = 10) {
        if (userManager.authState.canAccessAuthenticatedApis) {
            scope.launch {
                poolsCoordinator.updatePools()
            }
            scope.launch {
                poolsCoordinator.fetchSinceLatest(count)
            }
        }
    }

    override fun onCameraScanning(scanning: Boolean) {
        _state.update { it.copy(isCameraUp = scanning) }
    }

    override fun onCameraPermissionResult(result: PermissionResult) {
        _state.update { it.copy(isCameraPermissionGranted = result == PermissionResult.Granted) }
    }

    override fun showBill(bill: Bill) {
        if (bill.amount.converted.doubleValue == 0.0) return
        val owner = userManager.accountCluster ?: return

        if (!networkObserver.isConnected) {
            return ErrorUtils.showNetworkError(resources)
        }

        // Don't show action buttons for airdrop
        when (bill) {
            is Bill.Cash -> {
                when (bill.kind) {
                    Bill.Kind.airdrop -> {
                        // Don't show action buttons for airdrop
                        billController.update {
                            it.copy(
                                primaryAction = null,
                                secondaryAction = null,
                            )
                        }
                    }

                    Bill.Kind.cash -> {
                        if (bill.didReceive) {
                            // Don't show action buttons for received cash
                            billController.update {
                                it.copy(
                                    primaryAction = null,
                                    secondaryAction = null,
                                )
                            }
                        } else {
                            billController.update {
                                it.copy(
                                    primaryAction = BillState.Action.Send(
                                        action = {
                                            billController.cancelAwaitForGrab()

                                            shareGiftCard(bill.amount, owner) {
                                                trace(
                                                    tag = "Session",
                                                    message = "Cash link not sent. Restarting awaiting grab",
                                                    type = TraceType.User,
                                                )
                                                awaitBillGrab(bill, owner)
                                            }
                                        }
                                    ),
                                    // Allow cancelling pending outgoing cash bills
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
        }
    }

    private fun awaitBillGrab(bill: Bill, owner: AccountCluster) {
        billController.awaitGrab(
            amount = bill.amount,
            owner = owner,
            onGrabbed = {
                analytics.transfer(AnalyticsEvent.GiveBill, bill.amount)
                toastController.enqueue(bill.amount, isDeposit = false)
                dismissBill(Grabbed)
                vibrator.vibrate()
                bringActivityFeedCurrent()
            },
            onTimeout = {
                dismissBill(action = PutInWallet)
//                analytics.billTimeoutReached(
//                    bill.amount.kin,
//                    bill.amount.rate.currency,
//                    CodeAnalyticsManager.BillPresentationStyle.Slide
//                )
            },
            onError = {
                analytics.transfer(
                    event = AnalyticsEvent.GiveBill,
                    amount = bill.amount,
                    successful = false,
                    error = it
                )
                dismissBill(action = PutInWallet)
            },
            present = { data ->
                if (!bill.didReceive) {
                    trace(
                        tag = "Session",
                        message = "Pull out cash",
                        metadata = {
                            "amount" to bill.amount
                        },
                        type = TraceType.User,
                    )
                }
                presentBillToUser(data, bill)
            },
        )
    }

    private fun shareGiftCard(
        amount: LocalFiat,
        owner: AccountCluster,
        restartBillGrabber: () -> Unit
    ) {
        val giftCard = GiftCardAccount.create()
        val shareable = Shareable.CashLink(
            giftCardAccount = giftCard,
            amount = amount,
            autoConfirmationAfter = 60.seconds
        )

        scope.launch {
            shareSheetController.onShared = { result ->
                when (result) {
                    is ShareResult.ActionTaken -> {
                        scope.launch action@{
                            // immediately fund the gift card
                            val fundingResult = initiateGiftCardFunding(giftCard, owner, amount)
                            if (fundingResult.isFailure) {
                                return@action
                            }

                            // remain isChecking state until confirmation
                            shareSheetController.reset(setChecked = true)

                            // delay _slightly_ before presenting confirmation
                            delay(CASH_LINK_CONFIRMATION_DELAY)

                            confirmGiftCardSent(
                                owner = owner,
                                giftCard = giftCard,
                                amount = amount,
                                shareable = shareable,
                                result = result
                            )
                        }
                    }

                    ShareResult.NotShared -> {
                        restartBillGrabber()
                    }
                }
            }
            shareSheetController.present(shareable)
        }
    }

    private suspend fun confirmGiftCardSent(
        owner: AccountCluster,
        giftCard: GiftCardAccount,
        amount: LocalFiat,
        shareable: Shareable,
        result: ShareResult.ActionTaken,
    ) {
        // confirm the result of the share
        val confirmResult =
            shareConfirmationController.confirm(shareable, result)

        // reset isChecking after confirmation
        shareSheetController.reset(setChecked = false)

        when (confirmResult) {
            ShareConfirmationResult.Cancelled -> {
                BottomBarManager.showMessage(
                    title = "Are You Sure?",
                    subtitle = "Anyone you sent the link to won’t be able to collect the cash",
                    actions = listOf(
                        BottomBarAction(
                            text = "Yes",
                            onClick = {
                                // user selected cancel, dismiss everything back to camera
                                scope.launch {
                                    cancelGiftCard(owner, giftCard)
                                }
                            }
                        ),
                        BottomBarAction(
                            text = "Nevermind",
                            style = BottomBarManager.BottomBarButtonStyle.Text,
                            onClick = {
                                scope.launch {
                                    confirmGiftCardSent(
                                        owner,
                                        giftCard,
                                        amount,
                                        shareable,
                                        result
                                    )
                                }
                            }
                        )
                    ),
                    isDismissible = false,
                    showCancel = false,
                )
            }

            is ShareConfirmationResult.Confirmed -> {
                when (result) {
                    ShareResult.CopiedToClipboard -> {
                        toastController.enqueue(amount, isDeposit = false)
                        dismissBill(Grabbed)
                        vibrator.vibrate()
                        bringActivityFeedCurrent()
                        analytics.transfer(AnalyticsEvent.SentCashLink(clipboard = true), amount)
                        trace(
                            tag = "Session",
                            message = "Cash link copied to clipboard",
                            metadata = {
                                "amount" to amount
                            },
                            type = TraceType.User,
                        )
                    }

                    is ShareResult.SharedToApp -> {
                        toastController.enqueue(amount, isDeposit = false)
                        dismissBill(Grabbed)
                        vibrator.vibrate()
                        bringActivityFeedCurrent()
                        analytics.transfer(AnalyticsEvent.SentCashLink(app = result.to), amount)
                        trace(
                            tag = "Session",
                            message = "Cash link shared with ${result.to}",
                            metadata = {
                                "amount" to amount
                            },
                            type = TraceType.User,
                        )
                    }
                }
            }
        }
    }

    private suspend fun initiateGiftCardFunding(
        giftCard: GiftCardAccount,
        owner: AccountCluster,
        amount: LocalFiat,
    ): Result<LocalFiat> = suspendCancellableCoroutine { cont ->
        billController.fundGiftCard(
            giftCard = giftCard,
            amount = amount,
            owner = owner,
            onFunded = {
                shareSheetController.reset()
                cont.resume(Result.success(it))
            },
            onError = {
                dismissBill(PutInWallet)
                BottomBarManager.showError(
                    title = resources.getString(R.string.error_title_failedToCreateGiftCard),
                    message = resources.getString(R.string.error_description_failedToCreateGiftCard)
                )
                cont.resume(Result.failure(it))
            }
        )
    }

    private suspend fun cancelGiftCard(
        owner: AccountCluster,
        giftCard: GiftCardAccount
    ) {
        transactionController.cancelRemoteSend(
            vault = giftCard.cluster.vaultPublicKey,
            owner = owner,
        ).onFailure {
            dismissBill(PutInWallet)
        }.onSuccess {
            balanceController.fetchBalance()
            checkPendingItemsInFeed()
            dismissBill(PutInWallet)
        }
    }

    override fun onCodeScan(code: ScannableKikCode) {
        if (billController.state.value.bill != null) {
            return
        }

        val payload = (code as? ScannableKikCode.RemoteKikCode)?.payloadId?.toList() ?: return
        val codePayload = OpenCodePayload.fromList(payload)
        if (scannedRendezvous.contains(codePayload.rendezvous.publicKey)) {
            return
        }

        if (state.value.vibrateOnScan) {
            vibrator.tick()
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
            PayloadKind.Unknown -> Unit
        }
    }

    override fun openCashLink(cashLink: String?) {
        BottomBarManager.clear()

        val entropy = cashLink?.trim()?.replace("\n", "") ?: return
        val owner = userManager.accountCluster ?: return

        if (entropy.isEmpty()) {
            trace(
                tag = "Session",
                message = "Cash link empty",
                type = TraceType.Silent
            )
            return
        }

        claimGiftCard(owner = owner, entropy = entropy, claimIfOwned = false)
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
            onReceived = {
                analytics.transfer(AnalyticsEvent.ClaimedCashLink, amount = it)
                toastController.enqueue(it, isDeposit = true)
                showBill(
                    bill = Bill.Cash(amount = it, didReceive = true),
                )
                checkPendingItemsInFeed()
                bringActivityFeedCurrent()
            },
            onError = { cause ->
                if (cause !is ReceiveGiftTransactorError.UsersGiftCard) {
                    analytics.transfer(
                        AnalyticsEvent.ClaimedCashLink,
                        amount = null,
                        successful = false,
                        error = cause
                    )
                }

                when (cause) {
                    is ReceiveGiftTransactorError.UsersGiftCard -> {
                        // present confirmation to claim (cancel) own gift card
                        BottomBarManager.showMessage(
                            title = resources.getString(R.string.prompt_title_collectOwnCash),
                            subtitle = resources.getString(R.string.prompt_description_collectOwnCash),
                            isDismissible = false,
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
                                                // confirmed claim of own cash link
                                                claimIfOwned = true
                                            )
                                        }
                                    )
                                )
                            }
                        )
                    }

                    is ReceiveGiftTransactorError.AlreadyClaimed -> {
                        BottomBarManager.showError(
                            resources.getString(R.string.error_title_alreadyCollected),
                            resources.getString(R.string.error_description_alreadyCollected)
                        )
                    }

                    is ReceiveGiftTransactorError.Expired -> {
                        BottomBarManager.showError(
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
                analytics.transfer(AnalyticsEvent.GrabBill, amount)
                BottomBarManager.clear()
                toastController.enqueue(amount, isDeposit = true)
                showBill(
                    bill = Bill.Cash(amount = amount, didReceive = true),
                )
                checkPendingItemsInFeed()
                bringActivityFeedCurrent()
            },
            onError = {
                analytics.transfer(
                    event = AnalyticsEvent.GrabBill,
                    fiat = payload.fiat,
                    successful = false,
                    error = it
                )
                scannedRendezvous.remove(payload.rendezvous.publicKey)
                ErrorUtils.handleError(it)
            }
        )
    }

    private fun presentBillToUser(data: List<Byte>, bill: Bill) {
        if (billController.state.value.bill != null) return

        if (bill.didReceive) {
            billController.update {
                it.copy(
                    valuation = PaymentValuation(bill.amount.converted),
                )
            }

            vibrator.vibrate()
        }

        val style: BillDeterminationResult =
            if (bill.didReceive) Grabbed else PutInWallet

        _state.update { it.copy(billResult = style) }

        billController.update {
            it.copy(
                bill = Bill.Cash(
                    data = data,
                    amount = bill.amount,
                    didReceive = bill.didReceive,
                    confirmationDelay = bill.confirmationDelay,
                ),
                valuation = PaymentValuation(bill.amount.converted),
            )
        }

        if (style is BillDeterminationResult.ActedUpon) {
//            analytics.billShown(
//                bill.amountFloored.kin,
//                bill.amountFloored.rate.currency,
//                when (style) {
//                    PresentationStyle.Pop -> CodeAnalyticsManager.BillPresentationStyle.Pop
//                    PresentationStyle.Slide -> CodeAnalyticsManager.BillPresentationStyle.Slide
//                }
//            )
        }
    }


    override fun dismissBill(action: BillDeterminationResult) {
        scope.launch {
            _state.update { it.copy(billResult = action) }
            billController.reset()
            toastController.consumeQueue()
        }
    }
}

private val AIRDROP_INITIAL_DELAY = 1.seconds
private val CASH_LINK_CONFIRMATION_DELAY = 500.milliseconds