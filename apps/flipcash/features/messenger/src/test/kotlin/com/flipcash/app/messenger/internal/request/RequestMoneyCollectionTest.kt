package com.flipcash.app.messenger.internal.request

import com.flipcash.analytics.State as AnalyticsState
import com.flipcash.analytics.events.PaymentRequestEvents
import com.flipcash.app.analytics.RecordingAnalytics
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.services.chat.GroupContentCrypto
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.IdempotencyKey
import com.flipcash.services.models.paymentrequest.NewCollectionRequest
import com.flipcash.services.models.paymentrequest.NewPaymentRequest
import com.flipcash.services.models.paymentrequest.NewSplitRequest
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
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

/** Request Money under Group Money's flag: Amount Per Person, the confirm sheet, then a collection. */
@OptIn(ExperimentalCoroutinesApi::class)
class RequestMoneyCollectionTest {

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

    private val sent = mutableListOf<NewPaymentRequest>()
    private val keys = mutableListOf<IdempotencyKey>()

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
        coEvery { requests.create(capture(sent), capture(keys)) } returns Result.success(mockk(relaxed = true))
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
    )

    private val ten = Fiat(quarks = 10_000_000L, currencyCode = CurrencyCode.USD)

    private fun RequestMoneyViewModel.enter(amount: Int) {
        amountDelegate.reset()
        amount.toString().forEach { amountDelegate.onNumber(it.digitToInt()) }
        amountEntered()
    }

    @Test
    fun `with the flag on, Request Money opens Amount Per Person and Next opens the confirm sheet`() {
        val vm = viewModel()

        vm.open(chatId, collections = true)
        assertEquals(RequestMoneyViewModel.Stage.Amount, vm.state.value.stage)
        assertEquals(listOf<RequestStep>(RequestStep.Amount), stackFor(vm.state.value.stage, collections = true))

        vm.enter(10)

        assertEquals(RequestMoneyViewModel.Sheet.Confirm, vm.state.value.sheet)
        assertEquals(ten, vm.state.value.collection?.amount)
    }

    @Test
    fun `zero stays on Amount Per Person`() {
        val vm = viewModel()
        vm.open(chatId, collections = true)

        vm.amountEntered()

        assertEquals(RequestMoneyViewModel.Sheet.None, vm.state.value.sheet)
    }

    @Test
    fun `Send Request creates a collection of the amount entered, with no text, deadline or people`() {
        val vm = viewModel()
        vm.open(chatId, collections = true)
        vm.enter(10)

        vm.send()

        assertEquals(listOf<NewPaymentRequest>(NewCollectionRequest(chatId, ten)), sent)
        coVerify(exactly = 0) { crypto.sealPaymentRequestText(any(), any(), any(), any()) }
        coVerify(exactly = 1) { chatCoordinator.sendPaymentRequest(chatId, any()) }
        assertEquals(
            listOf(PaymentRequestEvents.created(0, AnalyticsState.SUCCESS, null, null)),
            analytics.events,
        )
    }

    @Test
    fun `with the flag off, Send Request still creates a split`() {
        val vm = viewModel()
        vm.open(chatId)
        vm.newRequest()
        vm.enter(5)
        vm.requestConfirm()

        vm.send()

        assertIs<NewSplitRequest>(sent.single())
    }

    @Test
    fun `back from the confirm sheet keeps Amount, and back from Amount leaves the flow`() {
        val vm = viewModel()
        vm.open(chatId, collections = true)
        vm.enter(10)

        assertEquals(true, vm.back())
        assertEquals(RequestMoneyViewModel.Sheet.None, vm.state.value.sheet)
        assertEquals(RequestMoneyViewModel.Stage.Amount, vm.state.value.stage)
        assertFalse(vm.back())
    }

    @Test
    fun `a retry after a failed create reuses the key while the amount is unchanged, and a new amount mints one`() {
        coEvery { requests.create(capture(sent), capture(keys)) } returns Result.failure(IllegalStateException("offline"))
        val vm = viewModel()
        vm.open(chatId, collections = true)

        vm.enter(10)
        vm.send()
        vm.enter(10)
        vm.send()
        vm.enter(12)
        vm.send()

        assertContentEquals(keys[0].bytes, keys[1].bytes)
        assertFalse(keys[1].bytes.contentEquals(keys[2].bytes))
    }

    @Test
    fun `a retry after the message failed sends the message again without creating again`() {
        val vm = viewModel()
        vm.open(chatId, collections = true)
        vm.enter(10)

        vm.send()
        vm.send()

        coVerify(exactly = 1) { requests.create(any(), any()) }
        coVerify(exactly = 2) { chatCoordinator.sendPaymentRequest(chatId, any()) }
    }
}
