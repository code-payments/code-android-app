package com.flipcash.app.messenger.internal.currencysend

import com.flipcash.app.analytics.FlipcashAnalytics
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.messenger.internal.payment.StartChattingPayer
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.payments.TipPaymentDelegate
import com.flipcash.shared.chat.ui.ConversationReference
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.exchange.VerifiedFiatCalculator
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.Token
import com.getcode.solana.keys.Mint
import com.getcode.util.resources.ResourceHelper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Next with more than the balance is stopped on the amount step. A send that fails after the swipe
 * leaves the sheet up: it is drawn in the route's window, so the modal lands over it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CurrencySendInsufficientFundsTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val exchange = mockk<Exchange>(relaxed = true)
    private val tokenCoordinator = mockk<TokenCoordinator>(relaxed = true)
    private val transactionController = mockk<TransactionController>(relaxed = true)
    private val chatCoordinator = mockk<ChatCoordinator>(relaxed = true)
    private val userManager = mockk<UserManager>(relaxed = true)
    private val resources = mockk<ResourceHelper>(relaxed = true)
    private val verifiedFiatCalculator = mockk<VerifiedFiatCalculator>(relaxed = true)
    private val tipPaymentDelegate = mockk<TipPaymentDelegate>(relaxed = true)
    private val analytics = mockk<FlipcashAnalytics>(relaxed = true)
    private val payer = mockk<StartChattingPayer>(relaxed = true)
    private val token = mockk<Token>(relaxed = true)
    private val mint = mockk<Mint>(relaxed = true)

    private var balance = Fiat(10.0)

    @Before
    fun setUp() {
        BottomBarManager.clear()
        every { exchange.preferredRate } returns Rate.oneToOne
        every { exchange.observePreferredRate() } returns emptyFlow()
        every { tokenCoordinator.cachedToken(any()) } returns token
        every { tokenCoordinator.balanceForToken(any<Token>()) } answers { balance }
        every { tokenCoordinator.balanceForToken(any<Mint>()) } returns flowOf(balance)
        every { transactionController.limits } returns MutableStateFlow(null)
        every { userManager.accountCluster } returns mockk<AccountCluster>(relaxed = true)
        every { chatCoordinator.feed(*anyVararg()) } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        BottomBarManager.clear()
    }

    private fun viewModel() = CurrencySendViewModel(
        exchange = exchange,
        tokenCoordinator = tokenCoordinator,
        transactionController = transactionController,
        chatCoordinator = chatCoordinator,
        userManager = userManager,
        resources = resources,
        verifiedFiatCalculator = verifiedFiatCalculator,
        tipPaymentDelegate = tipPaymentDelegate,
        analytics = analytics,
        payer = payer,
    ).also { it.start(mint) }

    private fun CurrencySendViewModel.type(vararg digits: Int) = digits.forEach(amountDelegate::onNumber)

    @Test
    fun `Next over balance presents insufficient funds and stays on the amount`() = runTest {
        val vm = viewModel()
        vm.type(5, 0) // 50 against a balance of 10

        vm.onAmountConfirmed()

        assertEquals(CurrencySendViewModel.Stage.Amount, vm.state.value.stage)
        assertNull(vm.state.value.amount)
        assertNull(vm.state.value.selected)
        verify(exactly = 1) { payer.presentInsufficientBalance(any()) }
    }

    @Test
    fun `Next within balance moves on to the recipients`() = runTest {
        val vm = viewModel()
        vm.type(5)

        vm.onAmountConfirmed()

        assertEquals(CurrencySendViewModel.Stage.Recipients, vm.state.value.stage)
        verify(exactly = 0) { payer.presentInsufficientBalance(any()) }
    }

    @Test
    fun `a send that fails after the swipe keeps the sheet open under the error`() = runTest {
        val vm = viewModel()
        vm.type(5)
        vm.onAmountConfirmed()
        val recipient = mockk<ConversationReference>(relaxed = true) { every { userId } returns listOf<Byte>(7) }
        vm.onRecipientTapped(recipient)
        coEvery { verifiedFiatCalculator.compute(any(), any(), any(), any()) } returns
            Result.failure(RuntimeException("stale"))
        vm.send()

        assertEquals(recipient, vm.state.value.selected)
        assertEquals(1, BottomBarManager.messages.value.size)
    }
}
