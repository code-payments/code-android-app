package com.flipcash.app.messenger.internal.request

import com.flipcash.analytics.State as AnalyticsState
import com.flipcash.analytics.events.PaymentRequestEvents
import com.flipcash.app.analytics.RecordingAnalytics
import com.flipcash.app.analytics.analytics
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.services.chat.GroupContentCrypto
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.GroupRosters
import com.flipcash.shared.chat.PaymentRequests
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.financial.Currency
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.util.resources.ResourceHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/** `Payment Request: Created`: once per confirm, on the create result, creator not counted. */
@OptIn(ExperimentalCoroutinesApi::class)
class RequestMoneyAnalyticsTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val viewer = listOf<Byte>(1)
    private val chatId = ChatId(ByteArray(1))
    private val analytics = RecordingAnalytics()
    private val requests = mockk<PaymentRequests>(relaxed = true)
    private val crypto = mockk<GroupContentCrypto>(relaxed = true)
    private val chatCoordinator = mockk<ChatCoordinator>(relaxed = true)
    private val exchange = mockk<Exchange>(relaxed = true)
    private val userManager = mockk<UserManager>(relaxed = true)
    private val rosters = mockk<GroupRosters>(relaxed = true)
    private val resources = mockk<ResourceHelper>(relaxed = true)

    private fun member(n: Int) = mockk<ChatMember>(relaxed = true) { every { userId } returns listOf(n.toByte()) }

    @Before
    fun setUp() {
        every { userManager.accountId } returns viewer
        every { exchange.preferredRate } returns Rate.oneToOne
        every { exchange.observePreferredRate() } returns emptyFlow()
        every { exchange.getCurrency(any()) } returns Currency(code = "USD", name = "US Dollar", symbol = "$")
        every { resources.getString(any<Int>()) } returns "text"
        every { resources.getString(any<Int>(), *anyVararg()) } returns "text"
        every { rosters.state } returns MutableStateFlow(mapOf(chatId to listOf(member(1), member(2), member(3))))
        coEvery { requests.resolvedPage(any(), any()) } returns Result.failure(IllegalStateException("no history"))
        coEvery { crypto.sealPaymentRequestText(any(), any(), any(), any()) } returns Result.success(mockk(relaxed = true))
    }

    private fun viewModel() = RequestMoneyViewModel(
        exchange = exchange,
        resources = resources,
        requests = requests,
        rosters = rosters,
        crypto = crypto,
        chatCoordinator = chatCoordinator,
        userManager = userManager,
        analytics = analytics,
    ).also { vm ->
        vm.open(chatId)
        vm.newRequest()
        vm.amountDelegate.onNumber(5)
        vm.amountEntered()
        vm.requestConfirm()
    }

    // $5 between the creator and two others: the largest share is $1.67.
    private val largestShare = Fiat(quarks = 1_670_000L, currencyCode = CurrencyCode.USD)

    @Test
    fun `a created request sends Created once, counting the people asked and not the creator`() {
        coEvery { requests.create(any(), any()) } returns Result.success(mockk(relaxed = true))
        val vm = viewModel()

        vm.send()

        assertEquals(
            listOf(PaymentRequestEvents.created(2, AnalyticsState.SUCCESS, largestShare.analytics, null)),
            analytics.events,
        )
    }

    @Test
    fun `a rejected create sends Created as a failure with the error`() {
        coEvery { requests.create(any(), any()) } returns Result.failure(IllegalStateException("denied"))
        val vm = viewModel()

        vm.send()

        assertEquals(
            listOf(PaymentRequestEvents.created(2, AnalyticsState.FAILURE, largestShare.analytics, "denied")),
            analytics.events,
        )
    }

    @Test
    fun `retrying after the message failed does not send Created again`() {
        coEvery { requests.create(any(), any()) } returns Result.success(mockk(relaxed = true))
        val vm = viewModel()

        vm.send()
        vm.send()

        assertEquals(1, analytics.events.size)
        coVerify(exactly = 1) { requests.create(any(), any()) }
    }
}
