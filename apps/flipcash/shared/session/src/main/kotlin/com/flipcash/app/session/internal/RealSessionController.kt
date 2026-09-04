package com.flipcash.app.session.internal

import com.flipcash.shared.transactionhistory.ActivityFeedCoordinator
import com.flipcash.shared.transactionhistory.ActivityFeedUpdater
import com.flipcash.app.appsettings.AppSettingValue
import com.flipcash.app.appsettings.AppSettingsCoordinator
import com.flipcash.app.billing.BillingClient
import com.flipcash.app.blocklist.BlocklistCoordinator
import com.flipcash.app.contacts.ContactCoordinator
import com.flipcash.app.blob.BlobStorageCoordinator
import com.flipcash.app.core.media.MediaUrlResolver
import com.flipcash.services.models.chat.ChatType
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.app.core.bill.Scannable
import com.flipcash.app.core.internal.bill.BillController
import com.flipcash.app.core.internal.updater.ProfileUpdater
import com.flipcash.app.core.tipping.TipCardOwner
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.session.BillOperations
import com.flipcash.app.session.CashLinkOperations
import com.flipcash.app.session.CodeScanOperations
import com.flipcash.app.session.DepositOperations
import com.flipcash.app.session.PutInWallet
import com.flipcash.app.session.SessionController
import com.flipcash.app.session.SessionState
import com.flipcash.app.session.TipCardOperations
import com.flipcash.app.session.internal.delegates.BillPresentationDelegate
import com.flipcash.app.session.internal.delegates.CashLinkDelegate
import com.flipcash.app.session.internal.delegates.CodeScanDelegate
import com.flipcash.app.session.internal.delegates.DepositDelegate
import com.flipcash.app.session.internal.delegates.GiftCardSharingDelegate
import com.flipcash.app.session.internal.delegates.TipCardDelegate
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
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
 * Thin orchestration shell that implements [SessionController] by composing six
 * focused delegates via Kotlin `by` interface delegation:
 *
 * | Delegate | Interface | Responsibility |
 * |----------|-----------|----------------|
 * | [com.flipcash.app.session.internal.delegates.BillPresentationDelegate] | [BillOperations] | Creating, presenting, and dismissing cash bills (and presenting resolved tip cards) |
 * | [com.flipcash.app.session.internal.delegates.CodeScanDelegate] | [CodeScanOperations] | QR/Kik-code scanning and grab attempts |
 * | [com.flipcash.app.session.internal.delegates.CashLinkDelegate] | [CashLinkOperations] | Cash-link claiming |
 * | [com.flipcash.app.session.internal.delegates.DepositDelegate] | [DepositOperations] | Deposit options and USDC sweep |
 * | [com.flipcash.app.session.internal.delegates.TipCardDelegate] | [TipCardOperations] | Resolving another user's tip card for presentation |
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
    private val tippingDelegate: TipCardDelegate,
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
    private val blocklistCoordinator: BlocklistCoordinator,
    private val blobStorageCoordinator: BlobStorageCoordinator,
    private val mediaUrlResolver: MediaUrlResolver,
    networkObserver: NetworkConnectivityListener,
    featureFlagController: FeatureFlagController,
    appSettingsCoordinator: AppSettingsCoordinator,
    dispatchers: DispatcherProvider,
) : SessionController, BillOperations by billDelegate,
    CodeScanOperations by scanDelegate,
    CashLinkOperations by cashLinkDelegate,
    DepositOperations by depositDelegate,
    TipCardOperations by tippingDelegate {

    private val scope = CoroutineScope(dispatchers.IO + SupervisorJob())

    /** In-flight feed catch-up, so a repeated foreground edge joins it instead of duplicating it. */
    private var feedCatchUpJob: Job? = null

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
                    is CodeScanDelegate.Event.TipCardScanned ->
                        resolveTipCard(TipCardOwner.ById(event.userId))
                }
            }.launchIn(scope)

        // Tip card resolved (via scan or deeplink) → hand to the bill delegate to present.
        // Presentation is already balance-gated in TipCardDelegate, so an unaffordable
        // tip never reaches here (no card to dismiss).
        tippingDelegate.events
            .onEach { event ->
                when (event) {
                    is TipCardDelegate.Event.Present -> billDelegate.presentTipCard(event.card)
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
                        scope.launch { chatCoordinator.teardown() }
                        // Blob download URLs are minted for the signed-in owner.
                        scope.launch { mediaUrlResolver.reset() }
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
            .flatMapLatest { chatCoordinator.observeUnreadConversations(ChatType.CONTACT_DM) }
            .distinctUntilChanged()
            .onEach { count -> stateHolder.update { it.copy(contactDmUnreadCount = count) } }
            .launchIn(scope)

        userManager.state
            .map { it.authState }
            .filter { it.isAtLeastRegistered }
            .distinctUntilChanged()
            .flatMapLatest { chatCoordinator.observeUnreadConversations(ChatType.TIP_DM) }
            .distinctUntilChanged()
            .onEach { count -> stateHolder.update { it.copy(tipsUnreadCount = count) } }
            .launchIn(scope)

        // Preload the blob upload policy once registered so profile-photo selection can filter and
        // validate against it without a network round-trip. Cached in the BlobStorageCoordinator.
        userManager.state
            .map { it.authState }
            .filter { it.isAtLeastRegistered }
            .distinctUntilChanged()
            .onEach { blobStorageCoordinator.preloadPolicy() }
            .launchIn(scope)

        featureFlagController.observe(FeatureFlag.ShowNetworkState)
            .onEach { enabled -> stateHolder.update { it.copy(showNetworkOffline = enabled) } }
            .launchIn(scope)

        featureFlagController.observe(FeatureFlag.VibrateOnScan)
            .onEach { enabled -> stateHolder.update { it.copy(vibrateOnScan = enabled) } }
            .launchIn(scope)

        tokenCoordinator.tokenBalances
            .map { tokenCoordinator.hasGiveableBalance() }
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
        refreshBlocklist()
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
        if (!shareSheetController.isCheckingForShare ||
            (bill != null && (bill as? Scannable.Payable)?.didReceive != true)) {
            BottomBarManager.clear()
            billController.cancelAwaitForGrab()
            dismissBill(PutInWallet)
        }
    }

    private fun startPolling() {
        if (userManager.authState.canAccessAuthenticatedApis) {
            // No `startIn` on the balances: this is the only thing that fetches them after login
            // (TokenCoordinator.onUserLoggedIn just hydrates Room, which is empty on a fresh
            // account), and the wallet tab holds its spinner until the fetch lands. A head start
            // here was a second of the login spinner spent deliberately idle.
            tokenUpdater.poll(scope = scope, frequency = 20.seconds)
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

                                    // Display-name entry follows the access key and is
                                    // unrelated to IAP correction — leave it unchanged.
                                    AuthState.ResumePoint.DisplayName -> currentState.resumePoint
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

    /**
     * Reconciles the activity feed with the server.
     *
     * [count] only bites on a cold cache, where [ActivityFeedCoordinator.fetchSinceLatest] seeds
     * the newest [count] rows; with anything cached it pages *forward* from the newest row and
     * takes whatever has happened since. So the size is really "how much history a fresh login
     * waits for before the wallet can draw" — and the wallet previews three rows. The default
     * covers the history screen's first page (it pages at 20) and leaves the rest to that screen's
     * own paging, rather than making every login pay for a hundred rows up front.
     *
     * Guarded against overlap because the foreground edge can arrive twice at login — from the
     * transition into [AuthState.Ready] and from `ON_RESUME` — and the second one would otherwise
     * duplicate the fetch rather than wait for it.
     */
    private fun bringActivityFeedCurrent(count: Int = FEED_CATCH_UP_PAGE) {
        if (!userManager.authState.canAccessAuthenticatedApis) return
        if (feedCatchUpJob?.isActive == true) return

        feedCatchUpJob = scope.launch {
            feedCoordinator.fetchSinceLatest(count)
        }
    }

    private fun refreshBlocklist() {
        if (userManager.authState.canAccessAuthenticatedApis) {
            scope.launch {
                blocklistCoordinator.refresh()
            }
        }
    }

    private companion object {
        /** Rows a fresh login seeds the activity feed with. See [bringActivityFeedCurrent]. */
        const val FEED_CATCH_UP_PAGE = 25
    }
}
