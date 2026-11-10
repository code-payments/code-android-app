package com.flipcash.app.messenger.internal.request

import androidx.lifecycle.ViewModelStore
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.paymentrequest.PaymentRequest
import com.flipcash.services.models.paymentrequest.PaymentRequestStatus
import com.flipcash.services.models.paymentrequest.PaymentRequestText
import com.flipcash.services.models.paymentrequest.PaidShare
import com.flipcash.services.models.paymentrequest.Share
import com.flipcash.services.models.paymentrequest.Split
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.PaymentRequests
import com.flipcash.shared.chat.ui.request.RequestMotion
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * `viewModelScope` runs on `Main.immediate`, so the deck collector emits inside the constructor.
 * An unconfined main dispatcher reproduces that: the ViewModel has to be fully initialized by then.
 * The clock runs on the test scheduler, so an `ends_at` arrives with virtual time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatRequestsViewModelTest {

    private val scheduler = TestCoroutineScheduler()
    private val start = Instant.fromEpochSeconds(1_800_000_000)
    private val clock = object : Clock {
        override fun now(): Instant = start + scheduler.currentTime.milliseconds
    }
    private val chat = ChatId(ByteArray(32) { 3 })
    private val viewer: ID = List(16) { 2 }
    private val inChat = MutableStateFlow<List<PaymentRequest>>(emptyList())

    private val requests = mockk<PaymentRequests>(relaxed = true) {
        every { state } returns MutableStateFlow(emptyMap())
        every { missing } returns MutableStateFlow(emptySet())
        every { inChat(any()) } returns inChat
    }
    private val chatCoordinator = mockk<ChatCoordinator>(relaxed = true) {
        every { observeMessages(any()) } returns emptyFlow()
    }
    private val userManager = mockk<UserManager>(relaxed = true) {
        every { accountId } returns viewer
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun request(
        id: Int,
        endsAt: Instant = start + 7.days,
        status: PaymentRequestStatus = PaymentRequestStatus.Open,
        viewerPaid: Boolean = false,
        version: Long = 1,
    ) = PaymentRequest(
        id = List(16) { id.toByte() },
        creator = List(16) { 1 },
        createdAt = start,
        status = status,
        closedAt = if (status == PaymentRequestStatus.Open) null else start,
        version = version,
        kind = PaymentRequest.Kind.GroupSplit(
            chatId = chat,
            split = Split(
                text = PaymentRequestText.Plain("Dinner", ""),
                currency = CurrencyCode.USD,
                endsAt = endsAt,
                shares = listOf(
                    Share(List(16) { 1 }, Fiat(20.0, CurrencyCode.USD), PaidShare(start, PaidShare.How.Creator)),
                    Share(viewer, Fiat(20.0, CurrencyCode.USD), if (viewerPaid) PaidShare(start, PaidShare.How.Payment) else null),
                ),
            ),
        ),
    )

    /** Runs [body] against a fresh view model, then clears it so its minute ticker stops. */
    private fun withViewModel(body: suspend TestScope.(ChatRequestsViewModel) -> Unit) = runTest(UnconfinedTestDispatcher(scheduler)) {
        val store = ViewModelStore()
        val viewModel = ChatRequestsViewModel(requests, chatCoordinator, userManager, clock)
        store.put("requests", viewModel)
        try {
            viewModel.attach(chat)
            runCurrent()
            body(viewModel)
        } finally {
            store.clear()
        }
    }

    @Test
    fun `constructs when the deck collector emits in place`() = withViewModel { viewModel ->
        assertTrue(viewModel.deck.value.isEmpty())
    }

    // 58
    @Test
    fun `the only request completing sweeps, leaves, and collapses the expanded list`() = withViewModel { viewModel ->
        inChat.value = listOf(request(1))
        viewModel.setExpanded(true)
        assertTrue(viewModel.expanded.value)

        val completed = request(1, status = PaymentRequestStatus.Completed, viewerPaid = true, version = 2)
        inChat.value = listOf(completed)
        // Held in its slot while the ring sweeps to full.
        assertEquals(listOf(completed), viewModel.deck.value)
        assertTrue(viewModel.expanded.value)

        advanceTimeBy(RequestMotion.CHANGE_MILLIS.toLong() + 1)
        assertTrue(viewModel.deck.value.isEmpty())
        assertFalse(viewModel.expanded.value)
    }

    @Test
    fun `a closed request leaves the deck at once`() = withViewModel { viewModel ->
        val other = request(2, endsAt = start + 2.days)
        inChat.value = listOf(request(1), other)
        inChat.value = listOf(request(1, status = PaymentRequestStatus.Closed, version = 2), other)
        assertEquals(listOf(other), viewModel.deck.value)
    }

    // 59
    @Test
    fun `a chat opened with ended requests shows only the open one`() = withViewModel { viewModel ->
        val open = request(1)
        inChat.value = listOf(
            request(2, status = PaymentRequestStatus.Completed, viewerPaid = true),
            open,
            request(3, status = PaymentRequestStatus.Closed),
            request(4, endsAt = start - 1.minutes),
        )
        assertEquals(listOf(open), viewModel.deck.value)
    }

    // 60
    @Test
    fun `the front request leaves at its ends_at, not the next minute`() = withViewModel { viewModel ->
        val front = request(1, endsAt = start + 90.seconds)
        val other = request(2, endsAt = start + 2.days)
        inChat.value = listOf(other, front)
        assertEquals(listOf(front, other), viewModel.deck.value)

        advanceTimeBy(89.seconds)
        assertEquals(listOf(front, other), viewModel.deck.value)

        // 90 s is between the minute ticks at 60 s and 120 s.
        advanceTimeBy(1.seconds + 1.milliseconds)
        assertEquals(listOf(other), viewModel.deck.value)
    }
}
