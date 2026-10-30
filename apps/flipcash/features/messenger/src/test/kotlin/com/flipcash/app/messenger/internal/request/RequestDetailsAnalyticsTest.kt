package com.flipcash.app.messenger.internal.request

import com.flipcash.analytics.State as AnalyticsState
import com.flipcash.analytics.events.PaymentRequestEvents
import com.flipcash.app.analytics.RecordingAnalytics
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.messenger.internal.payment.StartChattingPayer
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.GroupRosters
import com.flipcash.shared.chat.PaymentRequests
import com.flipcash.shared.payments.request.PaymentRequestPaymentDelegate
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.util.resources.ResourceHelper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/** `Payment Request: Closed`: on the close result, not on the confirm sheet, and not for mark-paid. */
@OptIn(ExperimentalCoroutinesApi::class)
class RequestDetailsAnalyticsTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val analytics = RecordingAnalytics()
    private val requests = mockk<PaymentRequests>(relaxed = true)
    private val payer = mockk<StartChattingPayer>(relaxed = true)
    private val transactionController = mockk<TransactionController>(relaxed = true)
    private val userManager = mockk<UserManager>(relaxed = true)
    private val rosters = mockk<GroupRosters>(relaxed = true)

    @Before
    fun setUp() {
        BottomBarManager.clear()
        every { userManager.accountId } returns listOf<Byte>(1)
        every { rosters.state } returns MutableStateFlow(emptyMap())
        every { payer.observeSelectedToken() } returns flowOf(mockk(relaxed = true))
        every { transactionController.limits } returns MutableStateFlow(null)
    }

    @After
    fun tearDown() = BottomBarManager.clear()

    private fun viewModel() = RequestDetailsViewModel(
        requests = requests,
        rosters = rosters,
        payments = mockk<PaymentRequestPaymentDelegate>(relaxed = true),
        payer = payer,
        tokenCoordinator = mockk<TokenCoordinator>(relaxed = true),
        transactionController = transactionController,
        exchange = mockk<Exchange>(relaxed = true),
        userManager = userManager,
        resources = mockk<ResourceHelper>(relaxed = true),
        analytics = analytics,
    ).also { it.open(ChatId(ByteArray(1)), "2") }

    @Test
    fun `closing a request sends Closed as a success`() {
        coEvery { requests.close(any()) } returns Result.success(mockk(relaxed = true))
        val vm = viewModel()

        vm.askClose()
        assertEquals(emptyList(), analytics.events)
        vm.close()

        assertEquals(listOf(PaymentRequestEvents.closed(AnalyticsState.SUCCESS, null)), analytics.events)
    }

    @Test
    fun `a failed close sends Closed as a failure with the error`() {
        coEvery { requests.close(any()) } returns Result.failure(IllegalStateException("gone"))
        val vm = viewModel()

        vm.close()

        assertEquals(listOf(PaymentRequestEvents.closed(AnalyticsState.FAILURE, "gone")), analytics.events)
    }

    @Test
    fun `marking a share paid sends nothing`() {
        coEvery { requests.markPaid(any(), any()) } returns Result.success(mockk(relaxed = true))
        val vm = viewModel()

        vm.markPaid(listOf<Byte>(3))

        assertEquals(emptyList(), analytics.events)
    }
}
