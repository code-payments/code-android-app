package com.flipcash.app.session.internal

import com.flipcash.app.appsettings.AppSettingsCoordinator
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.internal.bill.BillController
import com.flipcash.services.controllers.AccountController
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.session.internal.delegates.DepositDelegate
import com.flipcash.app.shareable.ShareSheetController
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.libs.coroutines.TestDispatcherProvider
import com.flipcash.services.user.AuthState
import com.flipcash.services.user.UserManager
import com.getcode.utils.network.ConnectionType
import com.getcode.utils.network.NetworkConnectivityListener
import com.getcode.utils.network.NetworkState
import com.getcode.utils.network.SignalStrength
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * A cash link's Sharesheet is translucent, so the activity underneath it is paused but never
 * stopped. `onAppInBackground` -- the only thing that reopens the once-per-foreground gate -- runs
 * on ON_STOP, so the resume that follows the Sharesheet finds the gate shut. Anything behind it is
 * skipped, and the share check is the one call there that has to run on *every* resume: it is what
 * notices a copy, and a copy that goes unnoticed leaves the gift card unfunded.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionControllerShareCheckTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    private val dispatchers = TestDispatcherProvider(UnconfinedTestDispatcher())

    // The observed state stays Unknown so that the collector's own `authState is Ready ->
    // onAppInForeground()` never fires and each test counts only the resumes it makes itself; the
    // direct getter, which is what onAppInForeground reads, answers Ready.
    private val userManager = mockk<UserManager>(relaxed = true) {
        every { state } returns MutableStateFlow(UserManager.State(authState = AuthState.Unknown))
        every { authState } returns AuthState.Ready
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

    private val networkObserver = mockk<NetworkConnectivityListener>(relaxed = true) {
        every { state } returns MutableStateFlow(
            NetworkState(
                connected = false,
                signalStrength = SignalStrength.Unknown,
                type = ConnectionType.Unknown,
            )
        )
    }

    private val accountController = mockk<AccountController>(relaxed = true) {
        coEvery { getUserFlags() } returns Result.failure(IllegalStateException("not under test"))
    }

    private val shareSheetController = mockk<ShareSheetController>(relaxed = true)
    private val depositDelegate = mockk<DepositDelegate>(relaxed = true)
    private val billController = mockk<BillController>(relaxed = true)

    @Before
    fun setUp() {
        every { billController.state } returns MutableStateFlow(mockk(relaxed = true))
    }

    @Test
    fun `a resume the Sharesheet did not stop the activity for still checks for a share`() = runTest {
        val controller = createController()

        controller.onAppInForeground()
        controller.onAppInForeground()
        advanceUntilIdle()

        verify(exactly = 2) { shareSheetController.checkForShare() }
    }

    @Test
    fun `the authenticated refresh still runs once per stay in the foreground`() = runTest {
        val controller = createController()

        controller.onAppInForeground()
        controller.onAppInForeground()
        advanceUntilIdle()

        verify(exactly = 1) { depositDelegate.sweepIfNeeded() }
    }

    @Test
    fun `an unauthenticated resume checks for nothing`() = runTest {
        every { userManager.authState } returns AuthState.Unknown
        val controller = createController()

        controller.onAppInForeground()
        advanceUntilIdle()

        verify(exactly = 0) { shareSheetController.checkForShare() }
    }

    private fun createController(): RealSessionController = RealSessionController(
        billDelegate = mockk(relaxed = true) {
            every { events } returns flowOf()
            every { billState } returns mockk(relaxed = true) {
                every { value } returns mockk(relaxed = true)
            }
        },
        scanDelegate = mockk(relaxed = true) { every { events } returns flowOf() },
        cashLinkDelegate = mockk(relaxed = true) { every { events } returns flowOf() },
        depositDelegate = depositDelegate,
        giftCardDelegate = mockk(relaxed = true) { every { events } returns flowOf() },
        tippingDelegate = mockk(relaxed = true),
        stateHolder = SessionStateHolder(),
        billController = billController,
        userManager = userManager,
        accountController = accountController,
        settingsController = mockk(relaxed = true),
        feedCoordinator = mockk(relaxed = true),
        networkObserver = networkObserver,
        tokenUpdater = mockk(relaxed = true),
        activityFeedUpdater = mockk(relaxed = true),
        profileUpdater = mockk(relaxed = true),
        shareSheetController = shareSheetController,
        toastController = mockk(relaxed = true),
        billingClient = mockk(relaxed = true),
        tokenCoordinator = tokenCoordinator,
        contactCoordinator = mockk(relaxed = true),
        chatCoordinator = mockk(relaxed = true),
        blocklistCoordinator = mockk(relaxed = true),
        blobStorageCoordinator = mockk(relaxed = true),
        mediaUrlResolver = mockk(relaxed = true),
        featureFlagController = featureFlagController,
        appSettingsCoordinator = appSettingsCoordinator,
        dispatchers = dispatchers,
    )
}
