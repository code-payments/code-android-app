package com.flipcash.app.session.internal

import com.flipcash.app.appsettings.AppSettingsCoordinator
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.bill.Bill
import com.flipcash.app.core.internal.bill.BillController
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.session.BillDeterminationResult
import com.flipcash.app.session.PutInWallet
import com.flipcash.app.session.internal.delegates.BillPresentationDelegate
import com.flipcash.app.session.internal.delegates.CashLinkDelegate
import com.flipcash.app.session.internal.delegates.CodeScanDelegate
import com.flipcash.app.session.internal.delegates.GiftCardSharingDelegate
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.libs.coroutines.TestDispatcherProvider
import com.flipcash.services.user.AuthState
import com.flipcash.services.user.UserManager
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.utils.network.NetworkState
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionControllerEventRoutingTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    // Controllable event flows for each delegate
    private val billDelegateEvents = MutableSharedFlow<BillPresentationDelegate.Event>(extraBufferCapacity = 8)
    private val scanDelegateEvents = MutableSharedFlow<CodeScanDelegate.Event>(extraBufferCapacity = 8)
    private val cashLinkDelegateEvents = MutableSharedFlow<CashLinkDelegate.Event>(extraBufferCapacity = 8)
    private val giftCardDelegateEvents = MutableSharedFlow<GiftCardSharingDelegate.Event>(extraBufferCapacity = 8)

    private val dispatchers = TestDispatcherProvider(UnconfinedTestDispatcher())

    private val userManager = mockk<UserManager>(relaxed = true) {
        every { state } returns MutableStateFlow(UserManager.State(authState = AuthState.Unknown))
        every { authState } returns AuthState.Unknown
    }

    private val featureFlagController = mockk<FeatureFlagController>(relaxed = true) {
        every { observe(any()) } returns MutableStateFlow(false)
    }

    private val appSettingsCoordinator = mockk<AppSettingsCoordinator>(relaxed = true) {
        every { observeValue(any()) } returns flowOf(false)
    }

    private val tokenCoordinator = mockk<TokenCoordinator>(relaxed = true) {
        every { tokenBalances } returns flowOf(emptyList())
        every { tokens } returns flowOf(emptyList())
    }

    private val networkObserver = mockk<com.getcode.utils.network.NetworkConnectivityListener>(relaxed = true) {
        every { state } returns MutableStateFlow(NetworkState(connected = false, signalStrength = com.getcode.utils.network.SignalStrength.Unknown, type = com.getcode.utils.network.ConnectionType.Unknown))
    }

    // Mocked delegates — relaxed so all method calls are no-ops by default
    private val billDelegate = mockk<BillPresentationDelegate>(relaxed = true) {
        every { events } returns billDelegateEvents
        every { billState } returns mockk(relaxed = true) {
            every { value } returns mockk(relaxed = true)
        }
    }

    private val scanDelegate = mockk<CodeScanDelegate>(relaxed = true) {
        every { events } returns scanDelegateEvents
    }

    private val cashLinkDelegate = mockk<CashLinkDelegate>(relaxed = true) {
        every { events } returns cashLinkDelegateEvents
    }

    private val giftCardDelegate = mockk<GiftCardSharingDelegate>(relaxed = true) {
        every { events } returns giftCardDelegateEvents
    }

    private val billController = mockk<BillController>(relaxed = true)

    @Before
    fun setUp() {
        // Ensure BillController.state is always non-null (accessed in onAppInBackground path)
        every { billController.state } returns MutableStateFlow(mockk(relaxed = true))
    }

    private fun createController(): RealSessionController {
        return RealSessionController(
            billDelegate = billDelegate,
            scanDelegate = scanDelegate,
            cashLinkDelegate = cashLinkDelegate,
            depositDelegate = mockk(relaxed = true),
            giftCardDelegate = giftCardDelegate,
            stateHolder = SessionStateHolder(),
            billController = billController,
            userManager = userManager,
            accountController = mockk(relaxed = true),
            settingsController = mockk(relaxed = true),
            feedCoordinator = mockk(relaxed = true),
            networkObserver = networkObserver,
            tokenUpdater = mockk(relaxed = true),
            activityFeedUpdater = mockk(relaxed = true),
            profileUpdater = mockk(relaxed = true),
            shareSheetController = mockk(relaxed = true),
            toastController = mockk(relaxed = true),
            billingClient = mockk(relaxed = true),
            tokenCoordinator = tokenCoordinator,
            contactCoordinator = mockk(relaxed = true),
            chatCoordinator = mockk(relaxed = true),
            featureFlagController = featureFlagController,
            appSettingsCoordinator = appSettingsCoordinator,
            dispatchers = dispatchers,
        )
    }

    // -------------------------------------------------------------------------
    // CashLinkDelegate.Event.BillReady → billDelegate.showBill
    // -------------------------------------------------------------------------

    @Test
    fun `CashLinkDelegate BillReady event routes to showBill on billDelegate`() = runTest {
        createController() // init block wires up collectors

        val bill = mockk<Bill>(relaxed = true)
        cashLinkDelegateEvents.emit(CashLinkDelegate.Event.BillReady(bill))

        advanceUntilIdle()

        verify(exactly = 1) { billDelegate.showBill(bill) }
    }

    // -------------------------------------------------------------------------
    // CodeScanDelegate.Event.BillReady → billDelegate.showBill
    // -------------------------------------------------------------------------

    @Test
    fun `CodeScanDelegate BillReady event routes to showBill on billDelegate`() = runTest {
        createController()

        val bill = mockk<Bill>(relaxed = true)
        scanDelegateEvents.emit(CodeScanDelegate.Event.BillReady(bill))

        advanceUntilIdle()

        verify(exactly = 1) { billDelegate.showBill(bill) }
    }

    // -------------------------------------------------------------------------
    // BillPresentationDelegate.Event.SendAsLinkRequested → giftCardDelegate.shareGiftCard
    // -------------------------------------------------------------------------

    @Test
    fun `BillPresentationDelegate SendAsLinkRequested event routes to shareGiftCard on giftCardDelegate`() = runTest {
        createController()

        val bill = mockk<Bill.Cash>(relaxed = true)
        val owner = mockk<AccountCluster>(relaxed = true)
        billDelegateEvents.emit(BillPresentationDelegate.Event.SendAsLinkRequested(bill, owner))

        advanceUntilIdle()

        verify(exactly = 1) { giftCardDelegate.shareGiftCard(bill, owner) }
    }

    // -------------------------------------------------------------------------
    // GiftCardSharingDelegate.Event.DismissBill → billDelegate.dismissBill
    // -------------------------------------------------------------------------

    @Test
    fun `GiftCardSharingDelegate DismissBill event routes to dismissBill on billDelegate`() = runTest {
        createController()

        val action: BillDeterminationResult = PutInWallet
        giftCardDelegateEvents.emit(GiftCardSharingDelegate.Event.DismissBill(action))

        advanceUntilIdle()

        // dismissBill on the shell delegates to billDelegate via `by`; verify the call landed there
        verify(exactly = 1) { billDelegate.dismissBill(action) }
    }

    // -------------------------------------------------------------------------
    // GiftCardSharingDelegate.Event.RestartBillGrab → billDelegate.awaitBillGrab
    // -------------------------------------------------------------------------

    @Test
    fun `GiftCardSharingDelegate RestartBillGrab event routes to awaitBillGrab on billDelegate`() = runTest {
        createController()

        val bill = mockk<Bill>(relaxed = true)
        val owner = mockk<AccountCluster>(relaxed = true)
        giftCardDelegateEvents.emit(GiftCardSharingDelegate.Event.RestartBillGrab(bill, owner))

        advanceUntilIdle()

        verify(exactly = 1) { billDelegate.awaitBillGrab(bill, owner) }
    }

    // -------------------------------------------------------------------------
    // Guard: unrelated events on one delegate do NOT trigger cross-delegate calls
    // -------------------------------------------------------------------------

    @Test
    fun `CodeScanDelegate RefreshFeed event does not call showBill`() = runTest {
        createController()

        scanDelegateEvents.emit(CodeScanDelegate.Event.RefreshFeed)

        advanceUntilIdle()

        verify(exactly = 0) { billDelegate.showBill(any()) }
    }

    @Test
    fun `CashLinkDelegate RefreshFeed event does not call showBill`() = runTest {
        createController()

        cashLinkDelegateEvents.emit(CashLinkDelegate.Event.RefreshFeed)

        advanceUntilIdle()

        verify(exactly = 0) { billDelegate.showBill(any()) }
    }

    @Test
    fun `GiftCardSharingDelegate RefreshFeed event does not call dismissBill or awaitBillGrab`() = runTest {
        createController()

        giftCardDelegateEvents.emit(GiftCardSharingDelegate.Event.RefreshFeed)

        advanceUntilIdle()

        verify(exactly = 0) { billDelegate.dismissBill(any()) }
        verify(exactly = 0) { billDelegate.awaitBillGrab(any(), any()) }
    }

    // -------------------------------------------------------------------------
    // Multiple events: each BillReady from a different delegate routes correctly
    // -------------------------------------------------------------------------

    @Test
    fun `multiple BillReady events from different delegates each call showBill once`() = runTest {
        createController()

        val billFromScan = mockk<Bill>(relaxed = true)
        val billFromLink = mockk<Bill>(relaxed = true)

        scanDelegateEvents.emit(CodeScanDelegate.Event.BillReady(billFromScan))
        cashLinkDelegateEvents.emit(CashLinkDelegate.Event.BillReady(billFromLink))

        advanceUntilIdle()

        verify(exactly = 1) { billDelegate.showBill(billFromScan) }
        verify(exactly = 1) { billDelegate.showBill(billFromLink) }
        verify(exactly = 2) { billDelegate.showBill(any()) }
    }
}
