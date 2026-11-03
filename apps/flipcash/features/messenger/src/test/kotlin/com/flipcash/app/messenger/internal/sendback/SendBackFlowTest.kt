package com.flipcash.app.messenger.internal.sendback

import com.flipcash.analytics.SendContext
import com.flipcash.analytics.State as AnalyticsState
import com.flipcash.analytics.events.TransferEvents
import com.flipcash.app.analytics.RecordingAnalytics
import com.flipcash.app.analytics.analytics
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.messenger.internal.payment.StartChattingPayer
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.CashMessageReturnStatus
import com.flipcash.services.models.chat.CashMessageReturnStatus.Status
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.MessageContent.Cash.PaymentReturn
import com.flipcash.services.models.paymentrequest.PaymentIntentId
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.models.SendBackRow
import com.flipcash.shared.payments.sendback.SendBackPaymentDelegate
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.exchange.VerifiedFiat
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Token
import com.getcode.solana.keys.Mint
import com.getcode.util.resources.ResourceHelper
import com.getcode.vendor.Base58
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Spec test cases 1–7, 10, 11, 13–15, 18 and 19. Case 12 is SendBackPaymentDelegateTest's. */
@OptIn(ExperimentalCoroutinesApi::class)
class SendBackFlowTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val payments = mockk<SendBackPaymentDelegate>(relaxed = true)
    private val payer = mockk<StartChattingPayer>(relaxed = true)
    private val tokenCoordinator = mockk<TokenCoordinator>(relaxed = true)
    private val userManager = mockk<UserManager>(relaxed = true)
    private val resources = mockk<ResourceHelper>(relaxed = true)
    private val token = mockk<Token>(relaxed = true)
    private val owner = mockk<AccountCluster>(relaxed = true)
    private val mint = mockk<Mint>(relaxed = true)
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val analytics = RecordingAnalytics()

    private val me: ID = listOf(1)
    private val peer: ID = listOf(2)
    private val chat = ChatId(ByteArray(32) { 3 })
    private val offeredId = 10L
    private val amount = Fiat("5.00", CurrencyCode.USD)
    private val intentBytes = ByteArray(32) { 7 }
    private val intentId = PaymentIntentId(intentBytes)

    /** Quarks the return needs at today's rate, and quarks the viewer holds. */
    private val needed = 500L
    private var held: Long? = 1_000L

    private fun pricedAt(quarks: Long) = mockk<VerifiedFiat>(relaxed = true) {
        every { localFiat.underlyingTokenAmount.quarks } returns quarks
    }

    private val priced = pricedAt(needed)

    private var statusCalls = 0
    private var statuses = ArrayDeque<Result<CashMessageReturnStatus>>()

    @Before
    fun setUp() {
        BottomBarManager.clear()
        every { userManager.accountId } returns me
        every { userManager.accountCluster } returns owner
        every { owner.withTimelockForToken(any()) } returns owner
        every { tokenCoordinator.cachedToken(any()) } returns token
        every { tokenCoordinator.heldQuarksForToken(any()) } answers { flowOf(held) }
        coEvery { payments.price(any(), any()) } returns Result.success(priced)
        every { resources.getString(R.string.error_title_staleRates) } returns RATE_UNAVAILABLE
        every { resources.getString(R.string.error_title_couldntSendBack) } returns COULDNT_SEND_BACK
        coEvery { payments.pay(any(), any(), any(), any(), any(), any(), any()) } returns Result.success(Unit)
    }

    @After
    fun tearDown() {
        scope.cancel()
        BottomBarManager.clear()
    }

    private fun status(s: Status, id: PaymentIntentId? = intentId) = Result.success(CashMessageReturnStatus(s, id))

    private fun answers(first: Result<CashMessageReturnStatus>, then: Result<CashMessageReturnStatus>? = null) {
        statuses = ArrayDeque(listOfNotNull(first, then))
    }

    private fun flow() = SendBackFlow(
        scope = scope,
        payments = payments,
        returnStatus = { _, _ ->
            statusCalls++
            statuses.removeFirstOrNull() ?: Result.failure(IllegalStateException("no status"))
        },
        payer = payer,
        tokenCoordinator = tokenCoordinator,
        userManager = userManager,
        resources = resources,
        analytics = analytics,
        chatId = { chat },
        peerName = { "Sally" },
        openScreen = {},
    )

    private fun cash(
        receiver: ID? = me,
        offer: Boolean = true,
        returnOf: Long? = null,
    ) = MessageContent.Cash(
        intentId = listOf(9),
        amount = amount,
        mint = mint,
        receiver = receiver,
        paymentReturn = PaymentReturn(
            offer = if (offer) PaymentReturn.Offer else null,
            returnOf = returnOf?.let { PaymentReturn.ReturnOf(it) },
        ),
    )

    /** The peer's offered payment to me, as the transcript notes it. */
    private fun SendBackFlow.noteOffer() = note(offeredId, cash(), senderId = peer, isFromSelf = false)

    private fun SendBackFlow.available(): SendBackFlow = apply {
        answers(status(Status.AVAILABLE))
        noteOffer()
    }

    private fun sentCash(state: AnalyticsState, error: String?) =
        TransferEvents.sentCash(state, amount.analytics, error, SendContext.SEND_BACK)

    // --- Rows (1–7) ---

    @Test
    fun `1 the receiver of an available offer sees Send Back`() {
        val f = flow().available()

        assertEquals(SendBackRow.Available, f.rows.value[offeredId])
        assertEquals(1, statusCalls)
    }

    @Test
    fun `2 the sender of an offer sees no row and makes no status call`() {
        val f = flow()
        answers(status(Status.AVAILABLE))

        f.note(offeredId, cash(receiver = peer), senderId = me, isFromSelf = true)

        assertNull(f.rows.value[offeredId])
        assertEquals(0, statusCalls)
    }

    @Test
    fun `2 a viewer who is not the single receiver makes no status call`() {
        val f = flow()

        f.note(offeredId, cash(receiver = listOf(5)), senderId = peer, isFromSelf = false)

        assertNull(f.rows.value[offeredId])
        assertEquals(0, statusCalls)
    }

    @Test
    fun `3 a cash message with no offer gets no row and no status call`() {
        val f = flow()

        f.note(offeredId, cash(offer = false), senderId = peer, isFromSelf = false)

        assertNull(f.rows.value[offeredId])
        assertEquals(0, statusCalls)
    }

    @Test
    fun `4 a return already in the transcript marks the offer Sent Back without a status call`() {
        val f = flow()
        answers(status(Status.AVAILABLE))

        // Newest first: the return is noted before the original it names.
        f.note(20L, cash(receiver = peer, offer = false, returnOf = offeredId), senderId = me, isFromSelf = true)
        f.noteOffer()

        assertEquals(SendBackRow.SentBack, f.rows.value[offeredId])
        assertEquals(0, statusCalls)
    }

    @Test
    fun `5 status RETURNED marks the offer Sent Back`() {
        val f = flow()
        answers(status(Status.RETURNED, id = null))

        f.noteOffer()

        assertEquals(SendBackRow.SentBack, f.rows.value[offeredId])
    }

    @Test
    fun `6 the original sender sees Sent Back once a return names their message`() {
        val f = flow()

        f.note(offeredId, cash(receiver = peer), senderId = me, isFromSelf = true)
        f.note(20L, cash(receiver = me, offer = false, returnOf = offeredId), senderId = peer, isFromSelf = false)

        assertEquals(SendBackRow.SentBack, f.rows.value[offeredId])
        assertEquals(0, statusCalls)
    }

    @Test
    fun `7 a failed status call leaves no row and shows no error`() {
        val f = flow()
        answers(Result.failure(IllegalStateException("offline")))

        f.noteOffer()

        assertNull(f.rows.value[offeredId])
        assertEquals(0, BottomBarManager.messages.value.size)
    }

    @Test
    fun `the status check runs once per DM open however often the message is mapped`() {
        val f = flow().available()

        f.noteOffer()
        f.noteOffer()

        assertEquals(1, statusCalls)
    }

    // --- Tap (10, 11) ---

    @Test
    fun `10 a tap with enough balance opens the sheet naming the peer`() {
        val f = flow().available()

        f.tap(offeredId)

        val sheet = assertNotNull(f.sheet.value)
        assertEquals("Sally", sheet.recipientName)
        assertEquals(amount, sheet.amount)
        assertEquals(listOf(TransferEvents.sendBackTapped(hasBalance = true)), analytics.events)
        verify(exactly = 0) { payer.presentInsufficientBalance(any(), any()) }
    }

    @Test
    fun `11 a tap with too little balance shows the modal instead of the sheet`() {
        held = needed - 1
        val f = flow().available()

        f.tap(offeredId)

        assertNull(f.sheet.value)
        verify(exactly = 1) { payer.presentInsufficientBalance(any(), any()) }
        assertEquals(listOf(TransferEvents.sendBackTapped(hasBalance = false)), analytics.events)
    }

    // --- Balance that can't be determined ---

    private fun noFreshRate() {
        coEvery { payments.price(any(), any()) } returns Result.failure(IllegalStateException("stale"))
    }

    @Test
    fun `a tap with no fresh rate shows Rate Unavailable and sends no Tapped event`() {
        noFreshRate()
        val f = flow().available()

        f.tap(offeredId)

        assertNull(f.sheet.value)
        assertEquals(listOf(RATE_UNAVAILABLE), BottomBarManager.messages.value.map { it.title })
        verify(exactly = 0) { payer.presentInsufficientBalance(any(), any()) }
        assertEquals(emptyList(), analytics.events)
    }

    @Test
    fun `a tap before the balance has loaded shows Rate Unavailable and sends no Tapped event`() {
        held = null
        val f = flow().available()

        f.tap(offeredId)

        assertNull(f.sheet.value)
        assertEquals(listOf(RATE_UNAVAILABLE), BottomBarManager.messages.value.map { it.title })
        verify(exactly = 0) { payer.presentInsufficientBalance(any(), any()) }
        assertEquals(emptyList(), analytics.events)
    }

    @Test
    fun `a swipe with no fresh rate closes the sheet with Rate Unavailable and pays nothing`() {
        val f = flow().available()
        f.tap(offeredId)
        noFreshRate()

        f.confirm()

        assertNull(f.sheet.value)
        assertEquals(SendBackRow.Available, f.rows.value[offeredId])
        assertEquals(listOf(RATE_UNAVAILABLE), BottomBarManager.messages.value.map { it.title })
        coVerify(exactly = 0) { payments.pay(any(), any(), any(), any(), any(), any(), any()) }
    }

    // --- Submit (13–15, 19) ---

    private fun SendBackFlow.swipe(): SendBackFlow = apply {
        tap(offeredId)
        analytics.events.clear()
        confirm()
    }

    @Test
    fun `13 a successful return marks the row Sent Back and sends Sent Cash Success`() {
        val f = flow().available().swipe()

        assertNull(f.sheet.value)
        assertEquals(SendBackRow.SentBack, f.rows.value[offeredId])
        assertEquals(listOf(sentCash(AnalyticsState.SUCCESS, null)), analytics.events)
        coVerify(exactly = 1) {
            payments.pay(chat, offeredId, peer, priced, token, owner, intentId)
        }
        // The id from the DM-open check is reused; no second status call.
        assertEquals(1, statusCalls)
    }

    @Test
    fun `the swipe pays the amount its balance check priced, without pricing again`() {
        val atTap = pricedAt(needed)
        val atSwipe = pricedAt(needed + 1)
        coEvery { payments.price(any(), any()) } returnsMany listOf(Result.success(atTap), Result.success(atSwipe))

        flow().available().swipe()

        coVerify(exactly = 1) { payments.pay(chat, offeredId, peer, atSwipe, token, owner, intentId) }
        // One price for the tap's check and one for the swipe's; none between the check and pay.
        coVerify(exactly = 2) { payments.price(amount, token) }
    }

    @Test
    fun `14 a failed return the server reports RETURNED is Sent Back with no error`() {
        coEvery { payments.pay(any(), any(), any(), any(), any(), any(), any()) } returns
            Result.failure(IllegalStateException("boom"))
        val f = flow().available()
        answers(status(Status.RETURNED, id = null))

        f.swipe()

        assertEquals(SendBackRow.SentBack, f.rows.value[offeredId])
        assertEquals(0, BottomBarManager.messages.value.size)
        assertEquals(listOf(sentCash(AnalyticsState.FAILURE, "Already Returned")), analytics.events)
    }

    @Test
    fun `15 a failed return still available goes back to Available with the error`() {
        coEvery { payments.pay(any(), any(), any(), any(), any(), any(), any()) } returns
            Result.failure(IllegalStateException("boom"))
        val f = flow().available()
        answers(status(Status.AVAILABLE))

        f.swipe()

        assertEquals(SendBackRow.Available, f.rows.value[offeredId])
        assertEquals(listOf(COULDNT_SEND_BACK), BottomBarManager.messages.value.map { it.title })
        assertEquals(listOf(sentCash(AnalyticsState.FAILURE, "boom")), analytics.events)
    }

    @Test
    fun `status already RETURNED at submit makes no payment and counts as Already Returned`() {
        val f = flow()
        // Available with no id: the submit has to ask again, and learns it was returned.
        answers(status(Status.AVAILABLE, id = null), status(Status.RETURNED, id = null))
        f.noteOffer()

        f.swipe()

        assertEquals(SendBackRow.SentBack, f.rows.value[offeredId])
        coVerify(exactly = 0) { payments.pay(any(), any(), any(), any(), any(), any(), any()) }
        assertEquals(listOf(sentCash(AnalyticsState.FAILURE, "Already Returned")), analytics.events)
    }

    @Test
    fun `19 a balance that drops while the sheet is open stops the swipe`() {
        val f = flow().available()
        f.tap(offeredId)
        held = needed - 1

        f.confirm()

        assertNull(f.sheet.value)
        assertEquals(SendBackRow.Available, f.rows.value[offeredId])
        verify(exactly = 1) { payer.presentInsufficientBalance(any(), any()) }
        coVerify(exactly = 0) { payments.pay(any(), any(), any(), any(), any(), any(), any()) }
    }

    // --- 18: no intent id outside the payment ---

    @Test
    fun `18 no event and no exposed state carries the intent id`() {
        coEvery { payments.pay(any(), any(), any(), any(), any(), any(), any()) } returns
            Result.failure(IllegalStateException("boom"))
        val f = flow().available()
        f.tap(offeredId)
        val sheet = f.sheet.value.toString()
        answers(status(Status.AVAILABLE))
        f.confirm()

        val base58 = Base58.encode(intentBytes)
        val forms = listOf(base58, intentBytes.toList().toString())
        val exposed = analytics.events.map { it.toString() } + sheet + f.rows.value.toString()
        for (text in exposed) for (form in forms) assertFalse(form in text, "intent id in: $text")
    }

    private companion object {
        const val RATE_UNAVAILABLE = "Rate Unavailable"
        const val COULDNT_SEND_BACK = "Couldn't Send Back"
    }
}
