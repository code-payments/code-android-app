package com.flipcash.app.session.internal.delegates

import com.flipcash.app.analytics.FlipcashAnalyticsService
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.bill.Bill
import com.flipcash.app.core.bill.BillState
import com.flipcash.app.core.internal.bill.BillController
import com.flipcash.app.session.PutInWallet
import com.flipcash.app.session.internal.SessionStateHolder
import com.flipcash.app.session.internal.toast.SessionToastController
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.core.R
import com.flipcash.libs.coroutines.TestDispatcherProvider
import com.flipcash.services.user.UserManager
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.util.resources.ResourceHelper
import com.getcode.util.vibration.Vibrator
import com.getcode.utils.network.NetworkConnectivityListener
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BillPresentationDelegateTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    private val billController = mockk<BillController>(relaxed = true)
    private val userManager = mockk<UserManager>(relaxed = true)
    private val analytics = mockk<FlipcashAnalyticsService>(relaxed = true)
    private val resources = mockk<ResourceHelper>(relaxed = true)
    private val tokenCoordinator = mockk<TokenCoordinator>(relaxed = true)
    private val networkObserver = mockk<NetworkConnectivityListener>(relaxed = true)
    private val vibrator = mockk<Vibrator>(relaxed = true)
    private val toastController = mockk<SessionToastController>(relaxed = true)
    private val dispatchers = TestDispatcherProvider(UnconfinedTestDispatcher())

    private val accountCluster = mockk<AccountCluster>(relaxed = true)

    private fun createDelegate(stateHolder: SessionStateHolder = SessionStateHolder()): BillPresentationDelegate {
        return BillPresentationDelegate(
            billController = billController,
            stateHolder = stateHolder,
            toastController = toastController,
            tokenCoordinator = tokenCoordinator,
            analytics = analytics,
            vibrator = vibrator,
            resources = resources,
            networkObserver = networkObserver,
            userManager = userManager,
            dispatchers = dispatchers,
        )
    }

    @Before
    fun setUp() {
        BottomBarManager.clear()
        every { userManager.accountCluster } returns accountCluster
        every { networkObserver.isConnected } returns true
        every { billController.state } returns mockk {
            every { value } returns BillState.Default
        }
    }

    @After
    fun tearDown() {
        BottomBarManager.clear()
    }

    // --- showBill guards ---

    @Test
    fun `showBill with zero amount does nothing`() = runTest {
        val amount = mockk<LocalFiat>(relaxed = true) {
            every { nativeAmount.decimalValue } returns 0.0
        }
        val bill = Bill.Cash(
            token = mockk(relaxed = true),
            amount = amount,
            didReceive = false,
            kind = Bill.Kind.cash,
        )

        val delegate = createDelegate()
        delegate.showBill(bill)

        verify(exactly = 0) { billController.update(any()) }
    }

    @Test
    fun `showBill without account cluster does nothing`() = runTest {
        every { userManager.accountCluster } returns null

        val amount = mockk<LocalFiat>(relaxed = true) {
            every { nativeAmount.decimalValue } returns 5.0
        }
        val bill = Bill.Cash(
            token = mockk(relaxed = true),
            amount = amount,
            didReceive = false,
            kind = Bill.Kind.cash,
        )

        val delegate = createDelegate()
        delegate.showBill(bill)

        verify(exactly = 0) { billController.update(any()) }
    }

    @Test
    fun `showBill without network does not update billController`() = runTest {
        every { networkObserver.isConnected } returns false

        val amount = mockk<LocalFiat>(relaxed = true) {
            every { nativeAmount.decimalValue } returns 5.0
        }
        val bill = Bill.Cash(
            token = mockk(relaxed = true),
            amount = amount,
            didReceive = false,
            kind = Bill.Kind.cash,
        )

        val delegate = createDelegate()
        delegate.showBill(bill)

        verify(exactly = 0) { billController.update(any()) }
    }

    // --- showBill action configuration ---

    @Test
    fun `showBill with airdrop sets null actions`() = runTest {
        val updateSlot = slot<(BillState) -> BillState>()
        every { billController.update(capture(updateSlot)) } answers {}

        val amount = mockk<LocalFiat>(relaxed = true) {
            every { nativeAmount.decimalValue } returns 5.0
        }
        val bill = Bill.Cash(
            token = mockk(relaxed = true),
            amount = amount,
            didReceive = false,
            kind = Bill.Kind.airdrop,
        )

        val delegate = createDelegate()
        delegate.showBill(bill)

        val updatedState = updateSlot.captured(BillState.Default)
        assertNull(updatedState.primaryAction)
        assertNull(updatedState.secondaryAction)
    }

    @Test
    fun `showBill with received cash sets null actions`() = runTest {
        val updateSlot = slot<(BillState) -> BillState>()
        every { billController.update(capture(updateSlot)) } answers {}

        val amount = mockk<LocalFiat>(relaxed = true) {
            every { nativeAmount.decimalValue } returns 5.0
        }
        val bill = Bill.Cash(
            token = mockk(relaxed = true),
            amount = amount,
            didReceive = true,
            kind = Bill.Kind.cash,
        )

        val delegate = createDelegate()
        delegate.showBill(bill)

        val updatedState = updateSlot.captured(BillState.Default)
        assertNull(updatedState.primaryAction)
        assertNull(updatedState.secondaryAction)
    }

    @Test
    fun `showBill with outgoing cash sets SendAsLink and Cancel actions`() = runTest {
        val updateSlot = slot<(BillState) -> BillState>()
        every { billController.update(capture(updateSlot)) } answers {}

        val amount = mockk<LocalFiat>(relaxed = true) {
            every { nativeAmount.decimalValue } returns 5.0
        }
        val bill = Bill.Cash(
            token = mockk(relaxed = true),
            amount = amount,
            didReceive = false,
            kind = Bill.Kind.cash,
        )

        val delegate = createDelegate()
        delegate.showBill(bill)

        val updatedState = updateSlot.captured(BillState.Default)
        assertIs<BillState.Action.SendAsLink>(updatedState.primaryAction)
        assertIs<BillState.Action.Cancel>(updatedState.secondaryAction)
    }

    // --- awaitGrab is called ---

    @Test
    fun `showBill with outgoing cash starts awaitGrab`() = runTest {
        val amount = mockk<LocalFiat>(relaxed = true) {
            every { nativeAmount.decimalValue } returns 5.0
        }
        val bill = Bill.Cash(
            token = mockk(relaxed = true),
            amount = amount,
            didReceive = false,
            kind = Bill.Kind.cash,
        )

        val delegate = createDelegate()
        delegate.showBill(bill)

        verify(exactly = 1) {
            billController.awaitGrab(
                amount = any(),
                token = any(),
                owner = any(),
                verifiedState = any(),
                nonce = any(),
                present = any(),
                onGrabbed = any(),
                onTimeout = any(),
                onError = any(),
            )
        }
    }

    // --- dismissBill ---

    @Test
    fun `dismissBill updates state and resets billController`() = runTest {
        val stateHolder = SessionStateHolder()
        val delegate = createDelegate(stateHolder)

        delegate.dismissBill(PutInWallet)

        assertEquals(PutInWallet, stateHolder.current.billResult)
        verify(exactly = 1) { billController.reset() }
    }

    // --- awaitBillGrab callbacks ---

    @Test
    fun `awaitBillGrab onTimeout dismisses bill`() = runTest {
        val onTimeoutSlot = slot<() -> Unit>()
        every {
            billController.awaitGrab(
                amount = any(),
                token = any(),
                owner = any(),
                verifiedState = any(),
                nonce = any(),
                present = any(),
                onGrabbed = any(),
                onTimeout = capture(onTimeoutSlot),
                onError = any(),
            )
        } answers {}

        val stateHolder = SessionStateHolder()
        val delegate = createDelegate(stateHolder)

        val amount = mockk<LocalFiat>(relaxed = true) {
            every { nativeAmount.decimalValue } returns 3.0
        }
        val bill = Bill.Cash(
            token = mockk(relaxed = true),
            amount = amount,
            didReceive = false,
            kind = Bill.Kind.cash,
        )

        delegate.awaitBillGrab(bill, accountCluster)

        onTimeoutSlot.captured.invoke()

        assertEquals(PutInWallet, stateHolder.current.billResult)
        verify(exactly = 1) { billController.reset() }
    }

    @Test
    fun `awaitBillGrab onError dismisses bill and shows error`() = runTest {
        every { resources.getString(R.string.error_title_CashReturnedToWallet) } returns "error_title_CashReturnedToWallet"
        every { resources.getString(R.string.error_description_CashReturnedToWallet) } returns "error_description_CashReturnedToWallet"

        val onErrorSlot = slot<(Throwable) -> Unit>()
        every {
            billController.awaitGrab(
                amount = any(),
                token = any(),
                owner = any(),
                verifiedState = any(),
                nonce = any(),
                present = any(),
                onGrabbed = any(),
                onTimeout = any(),
                onError = capture(onErrorSlot),
            )
        } answers {}

        val stateHolder = SessionStateHolder()
        val delegate = createDelegate(stateHolder)

        val amount = mockk<LocalFiat>(relaxed = true) {
            every { nativeAmount.decimalValue } returns 3.0
        }
        val bill = Bill.Cash(
            token = mockk(relaxed = true),
            amount = amount,
            didReceive = false,
            kind = Bill.Kind.cash,
        )

        delegate.awaitBillGrab(bill, accountCluster)

        onErrorSlot.captured.invoke(RuntimeException("give failed"))

        assertEquals(PutInWallet, stateHolder.current.billResult)
        verify(exactly = 1) { billController.reset() }

        val messages = BottomBarManager.messages.value
        assertTrue(messages.isNotEmpty(), "Expected an error message in BottomBarManager")
        assertEquals("error_title_CashReturnedToWallet", messages.first().title)
    }
}
