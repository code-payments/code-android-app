package com.flipcash.app.messenger.internal.request

import com.flipcash.analytics.SendContext
import com.flipcash.analytics.State as AnalyticsState
import com.flipcash.analytics.events.TransferEvents
import com.flipcash.app.analytics.analytics
import com.flipcash.app.analytics.RecordingAnalytics
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
import io.mockk.coVerify
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

/** The pay unit on its own: what the chat's deck Pay runs, with no Request details around it. */
@OptIn(ExperimentalCoroutinesApi::class)
class RequestPayFlowTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val payments = mockk<PaymentRequestPaymentDelegate>(relaxed = true)
    private val payer = mockk<StartChattingPayer>(relaxed = true)
    private val tokenCoordinator = mockk<TokenCoordinator>(relaxed = true)
    private val exchange = mockk<Exchange>(relaxed = true)
    private val userManager = mockk<UserManager>(relaxed = true)
    private val transactionController = mockk<TransactionController>(relaxed = true)
    private val resources = mockk<ResourceHelper>(relaxed = true)
    private val token = mockk<Token>(relaxed = true)
    private val owner = mockk<AccountCluster>(relaxed = true)
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val analytics = RecordingAnalytics()

    private var balance = Fiat(100.0)
    private var nothingToPay = 0
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
        every { payer.observeSelectedToken() } returns flowOf(token)
        every { exchange.rateFor(any()) } returns Rate.oneToOne
        every { tokenCoordinator.balanceForToken(any<Token>()) } answers { balance }
        every { transactionController.limits } returns MutableStateFlow(null)
        coEvery { payments.prepare(any(), any()) } returns Result.success(ready)
    }

    @After
    fun tearDown() {
        scope.cancel()
        BottomBarManager.clear()
    }

    private fun flow() = RequestPayFlow(
        scope = scope,
        payments = payments,
        payer = payer,
        tokenCoordinator = tokenCoordinator,
        transactionController = transactionController,
        exchange = exchange,
        userManager = userManager,
        resources = resources,
        analytics = analytics,
        openScreen = {},
        onNothingToPay = { nothingToPay++ },
    ).also { f -> scope.launch { f.token.collect {} } }

    @Test
    fun `a ready share opens the sheet even when the selected token is short`() {
        balance = Fiat(10.0)
        val f = flow()

        f.start(listOf<Byte>(2))

        assertEquals(true, f.state.value.open)
        verify(exactly = 0) { payer.presentInsufficientBalance(any(), any()) }
    }

    @Test
    fun `insufficient funds after the swipe presents the modal and keeps the sheet up`() {
        val f = flow()
        f.start(listOf<Byte>(2))
        balance = Fiat(10.0)

        f.confirm()

        assertEquals(true, f.state.value.open)
        verify(exactly = 1) { payer.presentInsufficientBalance(any(), any()) }
        coVerify(exactly = 0) { payments.pay(any(), any(), any()) }
    }

    @Test
    fun `a failed payment sends Sent Cash with the Payment Request context`() {
        coEvery { payments.pay(any(), any(), any()) } returns Result.failure(IllegalStateException("boom"))
        coEvery { payments.prepare(any(), any()) } returns Result.success(ready)
        val f = flow()
        f.start(listOf<Byte>(2))

        f.confirm()

        assertEquals(
            listOf(
                TransferEvents.sentCash(
                    AnalyticsState.FAILURE,
                    Fiat(50.0).analytics,
                    "boom",
                    SendContext.PAYMENT_REQUEST,
                ),
            ),
            analytics.events,
        )
    }

    @Test
    fun `a payment held back by the balance check sends nothing`() {
        val f = flow()
        f.start(listOf<Byte>(2))
        balance = Fiat(10.0)

        f.confirm()

        assertEquals(emptyList(), analytics.events)
    }

    @Test
    fun `a closed request presents the closed alert and never opens the sheet`() {
        coEvery { payments.prepare(any(), any()) } returns Result.success(ShareToPay.Closed(mockk(relaxed = true)))
        val f = flow()

        f.start(listOf<Byte>(2))

        assertEquals(false, f.state.value.open)
        assertEquals(1, BottomBarManager.messages.value.size)
    }

    @Test
    fun `nothing to pay reports it and never opens the sheet`() {
        coEvery { payments.prepare(any(), any()) } returns Result.success(ShareToPay.NothingToPay(mockk(relaxed = true)))
        val f = flow()

        f.start(listOf<Byte>(2))

        assertEquals(false, f.state.value.open)
        assertEquals(1, nothingToPay)
        assertEquals(0, BottomBarManager.messages.value.size)
    }
}
