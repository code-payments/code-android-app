package com.flipcash.app.messenger.internal.request

import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.messenger.internal.payment.StartChattingPayer
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.GroupRosters
import com.flipcash.shared.chat.PaymentRequests
import com.flipcash.shared.payments.request.PaymentRequestPaymentDelegate
import com.flipcash.shared.payments.request.ShareToPay
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.Token
import com.getcode.util.resources.ResourceHelper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Paying a request over balance is stopped before the swipe sheet opens. Anything that fails after
 * the swipe leaves the sheet up: it is drawn in the route's window, so the modal lands over it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RequestDetailsInsufficientFundsTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val payments = mockk<PaymentRequestPaymentDelegate>(relaxed = true)
    private val payer = mockk<StartChattingPayer>(relaxed = true)
    private val tokenCoordinator = mockk<TokenCoordinator>(relaxed = true)
    private val exchange = mockk<Exchange>(relaxed = true)
    private val userManager = mockk<UserManager>(relaxed = true)
    private val rosters = mockk<GroupRosters>(relaxed = true)
    private val transactionController = mockk<TransactionController>(relaxed = true)
    private val resources = mockk<ResourceHelper>(relaxed = true)
    private val token = mockk<Token>(relaxed = true)
    private val owner = mockk<AccountCluster>(relaxed = true)

    private var balance = Fiat(100.0)
    private val ready = ShareToPay.Ready(
        request = mockk(relaxed = true),
        share = mockk(relaxed = true) { every { amount } returns Fiat(50.0) },
        intentId = mockk(relaxed = true),
    )

    @Before
    fun setUp() {
        BottomBarManager.clear()
        every { userManager.accountId } returns listOf<Byte>(1)
        every { userManager.accountCluster } returns owner
        every { owner.withTimelockForToken(any()) } returns owner
        every { rosters.state } returns MutableStateFlow(emptyMap())
        every { payer.observeSelectedToken() } returns flowOf(token)
        every { exchange.rateFor(any()) } returns Rate.oneToOne
        every { tokenCoordinator.balanceForToken(any<Token>()) } answers { balance }
        every { transactionController.limits } returns MutableStateFlow(null)
        coEvery { payments.prepare(any(), any()) } returns Result.success(ready)
    }

    // The view model polls the clock on a loop, so these tests run eagerly on the unconfined Main
    // rather than under runTest, which would spin that loop through virtual time forever.
    private val subscribers = CoroutineScope(Dispatchers.Unconfined)

    /** `token` only holds a value while something collects it, as the screen does. */
    private fun collectToken(vm: RequestDetailsViewModel) {
        subscribers.launch { vm.token.collect {} }
    }

    @After
    fun tearDown() {
        subscribers.cancel()
        BottomBarManager.clear()
    }

    private fun viewModel() = RequestDetailsViewModel(
        requests = mockk<PaymentRequests>(relaxed = true),
        rosters = rosters,
        payments = payments,
        payer = payer,
        tokenCoordinator = tokenCoordinator,
        transactionController = transactionController,
        exchange = exchange,
        userManager = userManager,
        resources = resources,
    ).also { it.open(ChatId(ByteArray(1)), "2") }

    @Test
    fun `Pay opens the swipe sheet even when the selected token is short, since the token is picked in the sheet`() {
        balance = Fiat(10.0)
        val vm = viewModel()

        vm.payTapped()

        assertEquals(RequestDetailsViewModel.Sheet.Pay, vm.state.value.sheet)
        verify(exactly = 0) { payer.presentInsufficientBalance(any()) }
    }

    @Test
    fun `enough balance on Pay opens the swipe sheet without a modal`() {
        val vm = viewModel()

        vm.payTapped()

        assertEquals(RequestDetailsViewModel.Sheet.Pay, vm.state.value.sheet)
        verify(exactly = 0) { payer.presentInsufficientBalance(any()) }
    }

    @Test
    fun `balance dropping under the share after the sheet opened shows the modal over the open sheet`() {
        val vm = viewModel()
        collectToken(vm)
        vm.payTapped()
        assertEquals(RequestDetailsViewModel.Sheet.Pay, vm.state.value.sheet)

        balance = Fiat(10.0)
        vm.confirmPay()

        assertEquals(RequestDetailsViewModel.Sheet.Pay, vm.state.value.sheet)
        verify(exactly = 1) { payer.presentInsufficientBalance(any()) }
    }

    @Test
    fun `a failed payment keeps the sheet open under the error`() {
        val vm = viewModel()
        collectToken(vm)
        vm.payTapped()
        coEvery { payments.pay(any(), any(), any()) } returns Result.failure(RuntimeException("boom"))
        // The refetch after a failure finds the share still payable.
        coEvery { payments.prepare(any(), any()) } returns Result.success(ready)

        vm.confirmPay()

        assertEquals(RequestDetailsViewModel.Sheet.Pay, vm.state.value.sheet)
        assertEquals(1, BottomBarManager.messages.value.size)
    }
}
