package com.flipcash.app.session.internal.delegates

import app.cash.turbine.test
import com.flipcash.app.analytics.FlipcashAnalyticsService
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.bill.BillState
import com.flipcash.app.core.bill.Scannable
import com.flipcash.app.core.internal.bill.BillController
import com.flipcash.app.session.internal.SessionStateHolder
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.libs.coroutines.TestDispatcherProvider
import com.flipcash.services.user.UserManager
import com.getcode.opencode.internal.manager.VerifiedState
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.core.OpenCodePayload
import com.getcode.opencode.model.core.PayloadKind
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.network.NetworkConnectivityListener
import com.kik.kikx.models.ScannableKikCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class DelegateEventEmissionTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    private val billController = mockk<BillController>(relaxed = true)
    private val userManager = mockk<UserManager>(relaxed = true)
    private val analytics = mockk<FlipcashAnalyticsService>(relaxed = true)
    private val tokenCoordinator = mockk<TokenCoordinator>(relaxed = true)
    private val networkObserver = mockk<NetworkConnectivityListener>(relaxed = true)
    private val resources = mockk<ResourceHelper>(relaxed = true)
    private val dispatchers = TestDispatcherProvider(UnconfinedTestDispatcher())

    private val accountCluster = mockk<AccountCluster>(relaxed = true)

    @Before
    fun setUp() {
        every { userManager.accountCluster } returns accountCluster
        every { networkObserver.isConnected } returns true
        every { billController.state } returns mockk {
            every { value } returns BillState.Default
        }
    }

    @After
    fun tearDown() {}

    // --- BillPresentationDelegate events ---

    @Test
    fun `BillPresentationDelegate emits SendAsLinkRequested when send-as-link action invoked`() = runTest {
        val stateHolder = SessionStateHolder()
        val delegate = BillPresentationDelegate(
            billController = billController,
            stateHolder = stateHolder,
            toastController = mockk(relaxed = true),
            tokenCoordinator = tokenCoordinator,
            analytics = analytics,
            vibrator = mockk(relaxed = true),
            resources = resources,
            networkObserver = networkObserver,
            userManager = userManager,
            dispatchers = dispatchers,
        )

        val updateSlot = slot<(BillState) -> BillState>()
        every { billController.update(capture(updateSlot)) } answers {}

        val amount = mockk<LocalFiat>(relaxed = true) {
            every { nativeAmount.decimalValue } returns 5.0
        }
        val bill = Scannable.CashBill(
            token = mockk(relaxed = true),
            amount = amount,
            didReceive = false,
            kind = Scannable.Payable.Kind.cash,
            verifiedState = mockk(relaxed = true),
        )

        delegate.showBill(bill)

        // Extract the SendAsLink action and invoke it
        val updatedState = updateSlot.captured(BillState.Default)
        val sendAction = updatedState.primaryAction as BillState.Action.SendAsLink

        delegate.events.test {
            sendAction.action()
            val event = awaitItem()
            assertIs<BillPresentationDelegate.Event.SendAsLinkRequested>(event)
            assertEquals(bill, event.bill)
            assertEquals(accountCluster, event.owner)
        }
    }

    @Test
    fun `BillPresentationDelegate emits RefreshFeed on successful grab`() = runTest {
        val stateHolder = SessionStateHolder()
        val delegate = BillPresentationDelegate(
            billController = billController,
            stateHolder = stateHolder,
            toastController = mockk(relaxed = true),
            tokenCoordinator = tokenCoordinator,
            analytics = analytics,
            vibrator = mockk(relaxed = true),
            resources = resources,
            networkObserver = networkObserver,
            userManager = userManager,
            dispatchers = dispatchers,
        )

        val onGrabbedSlot = slot<suspend (LocalFiat) -> Unit>()
        every {
            billController.awaitGrab(
                amount = any(),
                token = any(),
                owner = any(),
                verifiedState = any(),
                nonce = any(),
                present = any(),
                onGrabbed = capture(onGrabbedSlot),
                onTimeout = any(),
                onError = any(),
            )
        } answers {}

        val amount = mockk<LocalFiat>(relaxed = true) {
            every { nativeAmount.decimalValue } returns 3.0
        }
        val bill = Scannable.CashBill(
            token = mockk(relaxed = true),
            amount = amount,
            didReceive = false,
            kind = Scannable.Payable.Kind.cash,
        )

        delegate.awaitBillGrab(bill, accountCluster)

        delegate.events.test {
            onGrabbedSlot.captured.invoke(amount)
            val event = awaitItem()
            assertIs<BillPresentationDelegate.Event.RefreshFeed>(event)
        }
    }

    // --- CodeScanDelegate events ---

    @Test
    fun `CodeScanDelegate emits BillReady and feed events on successful grab`() = runTest {
        mockkObject(OpenCodePayload.Companion)
        val mockPayload = mockk<OpenCodePayload>(relaxed = true) {
            every { kind } returns PayloadKind.Cash
            every { rendezvous.publicKey } returns "scan-event-test-key"
        }
        every { OpenCodePayload.fromList(any()) } returns mockPayload

        try {
            val stateHolder = SessionStateHolder()
            val delegate = CodeScanDelegate(
                stateHolder = stateHolder,
                billController = billController,
                tokenCoordinator = tokenCoordinator,
                analytics = analytics,
                vibrator = mockk(relaxed = true),
                userManager = userManager,
                dispatchers = dispatchers,
            )

            val onGrabbedSlot = slot<suspend (Token, LocalFiat, VerifiedState?) -> Unit>()
            every {
                billController.attemptGrab(
                    owner = any(),
                    payload = any(),
                    onGrabbed = capture(onGrabbedSlot),
                    onError = any(),
                )
            } answers {}

            val code = ScannableKikCode.RemoteKikCode(
                payloadId = ByteArray(20),
                colorIndex = 0
            )
            delegate.onCodeScan(code)

            val token = mockk<Token>(relaxed = true)
            val amount = mockk<LocalFiat>(relaxed = true)

            delegate.events.test {
                onGrabbedSlot.captured.invoke(token, amount, null)

                val billEvent = awaitItem()
                assertIs<CodeScanDelegate.Event.BillReady>(billEvent)

                val checkEvent = awaitItem()
                assertIs<CodeScanDelegate.Event.CheckPendingFeed>(checkEvent)

                val refreshEvent = awaitItem()
                assertIs<CodeScanDelegate.Event.RefreshFeed>(refreshEvent)
            }
        } finally {
            unmockkObject(OpenCodePayload.Companion)
        }
    }

    // --- CashLinkDelegate events ---

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `CashLinkDelegate emits BillReady and feed events on successful claim`() = runTest {
        val stateHolder = SessionStateHolder()
        val localBillController = mockk<BillController>(relaxed = true)
        val delegate = CashLinkDelegate(
            stateHolder = stateHolder,
            billController = localBillController,
            tokenCoordinator = tokenCoordinator,
            analytics = analytics,
            resources = resources,
            userManager = userManager,
        )

        val token = mockk<Token>(relaxed = true)
        val amount = mockk<LocalFiat>(relaxed = true)

        // Use answers + invocation args to work around MockK suspend lambda capture issues
        every {
            localBillController.receiveGiftCard(
                entropy = any(),
                owner = any(),
                claimIfOwned = any(),
                onReceived = any(),
                onError = any(),
            )
        } answers {
            val onReceived = args[3] as suspend (Token, LocalFiat) -> Unit
            runBlocking { onReceived(token, amount) }
        }

        delegate.events.test {
            delegate.openCashLink("validEntropy")

            val billEvent = awaitItem()
            assertIs<CashLinkDelegate.Event.BillReady>(billEvent)

            val checkEvent = awaitItem()
            assertIs<CashLinkDelegate.Event.CheckPendingFeed>(checkEvent)

            val refreshEvent = awaitItem()
            assertIs<CashLinkDelegate.Event.RefreshFeed>(refreshEvent)
        }
    }
}
