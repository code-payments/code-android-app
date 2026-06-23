package com.flipcash.app.session.internal.delegates

import app.cash.turbine.test
import com.flipcash.app.analytics.FlipcashAnalyticsService
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.bill.Bill
import com.flipcash.app.core.bill.BillState
import com.flipcash.app.core.internal.bill.BillController
import com.flipcash.app.session.PutInWallet
import com.flipcash.core.R
import com.flipcash.app.session.internal.toast.SessionToastController
import com.flipcash.app.shareable.ShareConfirmationResult
import com.flipcash.app.shareable.ShareResult
import com.flipcash.app.shareable.ShareSheetController
import com.flipcash.app.shareable.Shareable
import com.flipcash.app.shareable.ShareableConfirmationController
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.libs.coroutines.TestDispatcherProvider
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.internal.manager.VerifiedState
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.GiftCardAccount
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.util.resources.ResourceHelper
import com.getcode.util.vibration.Vibrator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class GiftCardSharingDelegateTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    private val billController = mockk<BillController>(relaxed = true)
    private val shareConfirmationController = mockk<ShareableConfirmationController>(relaxed = true)
    private val toastController = mockk<SessionToastController>(relaxed = true)
    private val tokenCoordinator = mockk<TokenCoordinator>(relaxed = true)
    private val transactionController = mockk<TransactionController>(relaxed = true)
    private val analytics = mockk<FlipcashAnalyticsService>(relaxed = true)
    private val vibrator = mockk<Vibrator>(relaxed = true)
    private val resources = mockk<ResourceHelper>(relaxed = true)
    private val accountCluster = mockk<AccountCluster>(relaxed = true)
    private val verifiedState = mockk<VerifiedState>(relaxed = true)
    private val amount = mockk<LocalFiat>(relaxed = true)
    private val token = mockk<Token>(relaxed = true)

    private lateinit var shareSheetController: ShareSheetController

    @Before
    fun setUp() {
        BottomBarManager.clear()

        every { billController.state } returns mockk {
            every { value } returns BillState.Default
        }

        every { resources.getString(R.string.action_yes) } returns "Yes"
        every { resources.getString(R.string.action_nevermind) } returns "Nevermind"

        mockkObject(GiftCardAccount.Companion)
        every { GiftCardAccount.create(any(), any()) } returns mockk(relaxed = true)

        shareSheetController = object : ShareSheetController {
            override val isCheckingForShare: Boolean = false
            override var onShared: ((ShareResult) -> Unit)? = null
            override fun checkForShare() {}
            override suspend fun present(shareable: Shareable) {}
            override fun reset(setChecked: Boolean) {}
        }
    }

    @After
    fun tearDown() {
        BottomBarManager.clear()
        unmockkObject(GiftCardAccount.Companion)
    }

    private fun TestScope.createDelegate(
        shareSheet: ShareSheetController = shareSheetController,
    ): GiftCardSharingDelegate {
        return GiftCardSharingDelegate(
            billController = billController,
            shareSheetController = shareSheet,
            shareConfirmationController = shareConfirmationController,
            toastController = toastController,
            tokenCoordinator = tokenCoordinator,
            transactionController = transactionController,
            analytics = analytics,
            vibrator = vibrator,
            resources = resources,
            dispatchers = TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler)),
        )
    }

    private fun makeBill(): Bill.Cash = Bill.Cash(
        token = token,
        amount = amount,
        didReceive = false,
        kind = Bill.Kind.cash,
        verifiedState = verifiedState,
    )

    // -------------------------------------------------------------------------
    // Test 1: NotShared emits RestartBillGrab
    // -------------------------------------------------------------------------

    @Test
    fun `shareGiftCard NotShared emits RestartBillGrab`() = runTest {
        val bill = makeBill()
        val delegate = createDelegate()

        delegate.events.test {
            delegate.shareGiftCard(bill, accountCluster)
            // onShared is assigned and present() was called synchronously via UnconfinedTestDispatcher
            shareSheetController.onShared?.invoke(ShareResult.NotShared)

            val event = awaitItem()
            assertIs<GiftCardSharingDelegate.Event.RestartBillGrab>(event)
        }
    }

    // -------------------------------------------------------------------------
    // Test 2: funding error emits DismissBill(PutInWallet)
    // -------------------------------------------------------------------------

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `shareGiftCard funding error emits DismissBill PutInWallet`() = runTest {
        every {
            billController.fundGiftCard(
                giftCard = any(),
                amount = any(),
                token = any(),
                owner = any(),
                verifiedState = any(),
                onFunded = any(),
                onError = any(),
            )
        } answers {
            val onError = args[6] as (Throwable) -> Unit
            onError(RuntimeException("funding failed"))
        }

        val bill = makeBill()
        val delegate = createDelegate()

        delegate.events.test {
            delegate.shareGiftCard(bill, accountCluster)
            // Trigger the share action so the funding path is entered
            shareSheetController.onShared?.invoke(ShareResult.CopiedToClipboard)

            val event = awaitItem()
            assertIs<GiftCardSharingDelegate.Event.DismissBill>(event)
            assertIs<PutInWallet>(event.action)
        }
    }

    // -------------------------------------------------------------------------
    // Test 3: cancelGiftCard success emits DismissBill(PutInWallet) and calls tokenCoordinator.update()
    // -------------------------------------------------------------------------

    @Test
    fun `cancelGiftCard success emits DismissBill PutInWallet and updates tokenCoordinator`() = runTest {
        coEvery {
            transactionController.cancelRemoteSend(
                vault = any(),
                owner = any(),
            )
        } returns Result.success(Unit)

        @Suppress("UNCHECKED_CAST")
        every {
            billController.fundGiftCard(
                giftCard = any(),
                amount = any(),
                token = any(),
                owner = any(),
                verifiedState = any(),
                onFunded = any(),
                onError = any(),
            )
        } answers {
            val onFunded = args[5] as suspend (LocalFiat) -> Unit
            runBlocking { onFunded(amount) }
        }

        coEvery {
            shareConfirmationController.confirm(any(), any())
        } returns ShareConfirmationResult.Cancelled

        val bill = makeBill()
        val delegate = createDelegate()

        delegate.events.test {
            delegate.shareGiftCard(bill, accountCluster)
            advanceUntilIdle()
            shareSheetController.onShared?.invoke(ShareResult.CopiedToClipboard)
            advanceUntilIdle()

            val messages = BottomBarManager.messages.value
            val yesAction = messages.firstOrNull()?.actions?.firstOrNull { it.text.toString() == "Yes" }
            yesAction?.onClick?.invoke()
            advanceUntilIdle()

            val event = awaitItem()
            assertIs<GiftCardSharingDelegate.Event.DismissBill>(event)
            assertIs<PutInWallet>(event.action)
        }

        coVerify { tokenCoordinator.update() }
    }

    // -------------------------------------------------------------------------
    // Test 4: cancelGiftCard failure emits DismissBill(PutInWallet)
    // -------------------------------------------------------------------------

    @Test
    fun `cancelGiftCard failure emits DismissBill PutInWallet`() = runTest {
        coEvery {
            transactionController.cancelRemoteSend(
                vault = any(),
                owner = any(),
            )
        } returns Result.failure(RuntimeException("cancel failed"))

        @Suppress("UNCHECKED_CAST")
        every {
            billController.fundGiftCard(
                giftCard = any(),
                amount = any(),
                token = any(),
                owner = any(),
                verifiedState = any(),
                onFunded = any(),
                onError = any(),
            )
        } answers {
            val onFunded = args[5] as suspend (LocalFiat) -> Unit
            runBlocking { onFunded(amount) }
        }

        coEvery {
            shareConfirmationController.confirm(any(), any())
        } returns ShareConfirmationResult.Cancelled

        val bill = makeBill()
        val delegate = createDelegate()

        delegate.events.test {
            delegate.shareGiftCard(bill, accountCluster)
            advanceUntilIdle()
            shareSheetController.onShared?.invoke(ShareResult.CopiedToClipboard)
            advanceUntilIdle()

            val messages = BottomBarManager.messages.value
            val yesAction = messages.firstOrNull()?.actions?.firstOrNull { it.text.toString() == "Yes" }
            yesAction?.onClick?.invoke()
            advanceUntilIdle()

            val event = awaitItem()
            assertIs<GiftCardSharingDelegate.Event.DismissBill>(event)
            assertIs<PutInWallet>(event.action)
        }
    }

    // -------------------------------------------------------------------------
    // Test 5: double funding is guarded by AtomicBoolean
    // -------------------------------------------------------------------------

    @Test
    fun `double funding is guarded by AtomicBoolean`() = runTest {
        val bill = makeBill()

        // Use a share sheet that fires onShared twice on present(), simulating the race
        // between shareResultReceiver and checkForShare().
        val racingShareSheet = object : ShareSheetController {
            override val isCheckingForShare: Boolean = false
            override var onShared: ((ShareResult) -> Unit)? = null
            override fun checkForShare() {}
            override suspend fun present(shareable: Shareable) {
                onShared?.invoke(ShareResult.CopiedToClipboard)
                onShared?.invoke(ShareResult.CopiedToClipboard)
            }
            override fun reset(setChecked: Boolean) {}
        }

        val delegate = createDelegate(shareSheet = racingShareSheet)
        delegate.shareGiftCard(bill, accountCluster)

        verify(exactly = 1) {
            billController.fundGiftCard(
                giftCard = any(),
                amount = any(),
                token = any(),
                owner = any(),
                verifiedState = any(),
                onFunded = any(),
                onError = any(),
            )
        }
    }
}
