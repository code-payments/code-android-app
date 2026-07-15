package com.flipcash.app.session.internal

import com.flipcash.app.activityfeed.ActivityFeedCoordinator
import com.flipcash.app.activityfeed.ActivityFeedUpdater
import com.flipcash.app.appsettings.AppSettingValue
import com.flipcash.app.appsettings.AppSettingsCoordinator
import com.flipcash.app.billing.BillingClient
import com.flipcash.app.contacts.ContactCoordinator
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.app.core.internal.bill.BillController
import com.flipcash.app.core.internal.updater.ProfileUpdater
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.session.BillOperations
import com.flipcash.app.session.CashLinkOperations
import com.flipcash.app.session.CodeScanOperations
import com.flipcash.app.session.DepositOperations
import com.flipcash.app.session.PutInWallet
import com.flipcash.app.session.SessionController
import com.flipcash.app.session.SessionState
import com.flipcash.app.session.internal.delegates.BillPresentationDelegate
import com.flipcash.app.session.internal.delegates.CashLinkDelegate
import com.flipcash.app.session.internal.delegates.CodeScanDelegate
import com.flipcash.app.session.internal.delegates.DepositDelegate
import com.flipcash.app.session.internal.delegates.GiftCardSharingDelegate
import com.flipcash.app.session.internal.toast.SessionToastController
import com.flipcash.app.shareable.ShareSheetController
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.app.tokens.TokenUpdater
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.controllers.AccountController
import com.flipcash.services.controllers.SettingsController
import com.flipcash.services.user.AuthState
import com.flipcash.services.user.UserManager
import com.getcode.manager.BottomBarManager
import com.getcode.ui.core.RestrictionType
import com.getcode.utils.TraceType
import com.getcode.utils.network.NetworkConnectivityListener
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

/**
 * Thin orchestration shell that implements [SessionController] by composing five
 * focused delegates via Kotlin `by` interface delegation:
 *
 * | Delegate | Interface | Responsibility |
 * |----------|-----------|----------------|
 * | [com.flipcash.app.session.internal.delegates.BillPresentationDelegate] | [BillOperations] | Creating, presenting, and dismissing cash bills |
 * | [com.flipcash.app.session.internal.delegates.CodeScanDelegate] | [CodeScanOperations] | QR/Kik-code scanning and grab attempts |
 * | [com.flipcash.app.session.internal.delegates.CashLinkDelegate] | [CashLinkOperations] | Cash-link claiming |
 * | [com.flipcash.app.session.internal.delegates.DepositDelegate] | [DepositOperations] | Deposit options and USDC sweep |
 * | [com.flipcash.app.session.internal.delegates.GiftCardSharingDelegate] | *(internal)* | "Send as Link" gift-card funding + share |
 *
 * **What lives here (and why):**
 * - **Event routing** — each delegate exposes a `Flow<Event>` (backed by a `Channel`); the `init` block
 *   collects all five and dispatches cross-delegate calls (e.g. scan-delegate's
 *   `BillReady` → `showBill`, bill-delegate's `SendAsLinkRequested` →
 *   `giftCardDelegate.shareGiftCard`). All cross-delegate wiring is visible in one
 *   place.
 * - **Lifecycle methods** — [onAppInForeground] / [onAppInBackground] are inherently
 *   cross-cutting (polling, billing, share-sheet, feed refresh) and stay on the shell.
 * - **Flow observers** — auth-state transitions, feature flags, network reconnects,
 *   and balance/token observations that update [SessionStateHolder].
 *
 * Delegates are fully self-contained after Hilt construction — no `lateinit`,
 * no `initialize()` calls, no post-construction wiring.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RealSessionController @Inject constructor(
    private val billDelegate: BillPresentationDelegate,
    private val scanDelegate: CodeScanDelegate,
    private val cashLinkDelegate: CashLinkDelegate,
    private val depositDelegate: DepositDelegate,
    private val giftCardDelegate: GiftCardSharingDelegate,
    private val stateHolder: SessionStateHolder,
    private val billController: BillController,
    private val userManager: UserManager,
    private val accountController: AccountController,
    private val settingsController: SettingsController,
    private val feedCoordinator: ActivityFeedCoordinator,
    private val tokenUpdater: TokenUpdater,
    private val activityFeedUpdater: ActivityFeedUpdater,
    private val profileUpdater: ProfileUpdater,
    private val shareSheetController: ShareSheetController,
    private val toastController: SessionToastController,
    private val billingClient: BillingClient,
    private val tokenCoordinator: TokenCoordinator,
    private val contactCoordinator: ContactCoordinator,
    private val chatCoordinator: ChatCoordinator,
    networkObserver: NetworkConnectivityListener,
    featureFlagController: FeatureFlagController,
    appSettingsCoordinator: AppSettingsCoordinator,
    dispatchers: DispatcherProvider,
) : SessionController, BillOperations by billDelegate,
    CodeScanOperations by scanDelegate,
    CashLinkOperations by cashLinkDelegate,
    DepositOperations by depositDelegate {

    private val scope = CoroutineScope(dispatchers.IO + SupervisorJob())

    override val state: StateFlow<SessionState>
        get() = stateHolder.state

    init {
        // Collect delegate events and dispatch cross-delegate calls
        billDelegate.events
            .onEach { event ->
                when (event) {
                    is BillPresentationDelegate.Event.SendAsLinkRequested ->
                        giftCardDelegate.shareGiftCard(event.bill, event.owner)
                    is BillPresentationDelegate.Event.RefreshFeed ->
                        bringActivityFeedCurrent()
                }
            }.launchIn(scope)

        scanDelegate.events
            .onEach { event ->
                when (event) {
                    is CodeScanDelegate.Event.BillReady -> showBill(event.bill)
                    is CodeScanDelegate.Event.RefreshFeed -> bringActivityFeedCurrent()
                    is CodeScanDelegate.Event.CheckPendingFeed -> checkPendingItemsInFeed()
                }
            }.launchIn(scope)

        cashLinkDelegate.events
            .onEach { event ->
                when (event) {
                    is CashLinkDelegate.Event.BillReady -> showBill(event.bill)
                    is CashLinkDelegate.Event.RefreshFeed -> bringActivityFeedCurrent()
                    is CashLinkDelegate.Event.CheckPendingFeed -> checkPendingItemsInFeed()
                }
            }.launchIn(scope)

        giftCardDelegate.events
            .onEach { event ->
                when (event) {
                    is GiftCardSharingDelegate.Event.DismissBill -> dismissBill(event.action)
                    is GiftCardSharingDelegate.Event.RestartBillGrab ->
                        billDelegate.awaitBillGrab(event.bill, event.owner)
                    is GiftCardSharingDelegate.Event.RefreshFeed -> bringActivityFeedCurrent()
                }
            }.launchIn(scope)

        // handle auth state transitions: cleanup on logout, start polling on login
        userManager.state
            .map { it.authState }
            .distinctUntilChanged()
            .onEach { authState ->
                when {
                    authState is AuthState.LoggedOut -> {
                        stopPolling()
                        depositDelegate.cancelSweep()
                        scope.launch { contactCoordinator.reset() }
                        scope.launch { chatCoordinator.reset() }
                        stateHolder.reset()
                    }

                    authState is AuthState.Ready -> {
                        onAppInForeground()
                    }

                    authState.isAtLeastRegistered -> {
                        updateUserFlags()
                    }
                }
            }.launchIn(scope)

        userManager.state
            .map { it.isTimelockUnlocked }
            .onEach { stateHolder.update { it.copy(restrictionType = RestrictionType.TIMELOCK_UNLOCKED) } }
            .launchIn(scope)

        userManager.state
            .map { it.authState }
            .filter { it.isAtLeastRegistered }
            .distinctUntilChanged()
            .filter { userManager.state.value.flags?.requiresIapForRegistration == true }
            .onEach { billingClient.connect() }
            .launchIn(scope)

        userManager.state
            .map { it.authState }
            .filter { it.isAtLeastRegistered }
            .distinctUntilChanged()
            .flatMapLatest { chatCoordinator.observeUnreadConversations() }
            .distinctUntilChanged()
            .onEach { count -> stateHolder.update { it.copy(notificationUnreadCount = count) } }
            .launchIn(scope)

        appSettingsCoordinator
            .observeValue(AppSettingValue.CameraStartByDefault)
            .onEach { autoStart -> stateHolder.update { it.copy(autoStartCamera = autoStart) } }
            .launchIn(scope)

        featureFlagController.observe(FeatureFlag.ShowNetworkState)
            .onEach { enabled -> stateHolder.update { it.copy(showNetworkOffline = enabled) } }
            .launchIn(scope)

        featureFlagController.observe(FeatureFlag.VibrateOnScan)
            .onEach { enabled -> stateHolder.update { it.copy(vibrateOnScan = enabled) } }
            .launchIn(scope)

        // Re-evaluate on balance changes and on GiveUsdf toggles — hasGiveableBalance()
        // filters out USDF when that flag is off.
        combine(
            tokenCoordinator.tokenBalances,
            featureFlagController.observe(FeatureFlag.GiveUsdf),
        ) { _, _ -> tokenCoordinator.hasGiveableBalance() }
            .distinctUntilChanged()
            .onEach { hasBalance -> stateHolder.update { it.copy(hasGiveableBalance = hasBalance) } }
            .launchIn(scope)

        tokenCoordinator.tokenBalances
            .map { tokenCoordinator.hasBalance() }
            .distinctUntilChanged()
            .onEach { hasBalance -> stateHolder.update { it.copy(hasBalance = hasBalance) } }
            .launchIn(scope)

        tokenCoordinator.tokens
            .onEach { tokens ->
                stateHolder.update { it.copy(tokens = tokens) }
            }.launchIn(scope)

        combine(
            featureFlagController.observe(FeatureFlag.PhoneNumberSend),
            userManager.state.map { it.flags?.enablePhoneNumberSend == true }
        ) { beta, server -> beta || server }
            .onEach { enabled -> stateHolder.update { it.copy(isPhoneNumberSendEnabled = enabled) } }
            .launchIn(scope)

        // Retry updateUserFlags when network is restored
        networkObserver.state
            .map { it.connected }
            .distinctUntilChanged()
            .filter { connected -> connected }
            .onEach {
                if (userManager.authState.isAtLeastRegistered) {
                    updateUserFlags()
                    depositDelegate.sweepIfNeeded()
                }
            }.launchIn(scope)
    }

    override fun onAppInForeground() {
        trace(
            tag = "Session",
            message = "onAppInForeground",
            type = TraceType.Process,
        )
        startPolling()
        depositDelegate.sweepIfNeeded()
        updateUserFlags()
        linkForPaymentIfNeeded()
        updateSettings()
        checkPendingItemsInFeed()
        bringActivityFeedCurrent()
        shareSheetController.checkForShare()
        if (userManager.authState.isAtLeastRegistered && userManager.state.value.flags?.requiresIapForRegistration == true) {
            billingClient.connect()
        }
    }

    override fun onAppInBackground() {
        stopPolling()
        depositDelegate.cancelSweep()
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
            tokenUpdater.poll(scope = scope, frequency = 20.seconds, startIn = 2.seconds)
            activityFeedUpdater.poll(scope = scope, frequency = 60.seconds, startIn = 60.seconds)
            profileUpdater.poll(scope = scope, frequency = 60.seconds, startIn = 0.seconds)
        }
    }

    private fun stopPolling() {
        tokenUpdater.stop()
        activityFeedUpdater.stop()
        profileUpdater.stop()
    }

    private fun updateUserFlags() {
        if (userManager.authState.isAtLeastRegistered) {
            scope.launch {
                accountController.getUserFlags()
                    .onSuccess { flags ->
                        userManager.set(flags)
                        val currentState = userManager.authState
                        when {
                            flags.isRegistered && !currentState.canAccessAuthenticatedApis
                                    && currentState !is AuthState.Onboarding -> {
                                userManager.set(authState = AuthState.Ready)
                            }
                            currentState is AuthState.Onboarding -> {
                                val corrected = when (currentState.resumePoint) {
                                    AuthState.ResumePoint.PostAccessKey ->
                                        if (flags.requiresIapForRegistration)
                                            AuthState.ResumePoint.AccessKeyThenPurchase
                                        else currentState.resumePoint

                                    AuthState.ResumePoint.AccessKeyThenPurchase ->
                                        if (!flags.requiresIapForRegistration)
                                            AuthState.ResumePoint.PostAccessKey
                                        else currentState.resumePoint

                                    AuthState.ResumePoint.AccessKey -> currentState.resumePoint

                                    // Phone verification precedes the access key and is
                                    // unrelated to IAP correction — leave it unchanged.
                                    AuthState.ResumePoint.PhoneNumber -> currentState.resumePoint
                                }
                                if (corrected != currentState.resumePoint) {
                                    userManager.set(authState = AuthState.Onboarding(corrected))
                                }
                            }
                        }
                    }
            }
        }
    }

    private fun linkForPaymentIfNeeded() {
        if (userManager.authState.canAccessAuthenticatedApis) {
            contactCoordinator.linkForPaymentIfNeeded()
        }
    }

    private fun updateSettings() {
        if (userManager.authState.canAccessAuthenticatedApis) {
            scope.launch {
                settingsController.update()
            }
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
}
