package com.flipcash.app.messenger.internal.request

import com.flipcash.analytics.SendContext
import com.flipcash.analytics.State as AnalyticsState
import com.flipcash.analytics.events.TransferEvents
import com.flipcash.app.analytics.analytics
import com.flipcash.app.analytics.RecordingAnalytics
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.messenger.internal.payment.StartChattingPayer
import com.flipcash.features.messenger.R
import com.flipcash.app.tokens.CollectionFundingPlan
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.app.tokens.FundingBalance
import com.flipcash.app.tokens.planCollectionFunding
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.GroupRosters
import com.flipcash.shared.chat.PaymentRequests
import com.flipcash.shared.payments.request.CollectionFundingError
import com.flipcash.shared.payments.request.CollectionTopUp
import com.flipcash.shared.payments.request.CollectionTopUpLocks
import com.flipcash.shared.payments.request.PaymentRequestPaymentDelegate
import com.flipcash.shared.payments.request.ShareToPay
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.usdf
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.base58
import com.getcode.util.resources.ResourceHelper
import io.mockk.MockKAnswerScope
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
import kotlin.test.assertNotNull

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
    private val topUp = CollectionTopUp(
        FakeCollectionTopUpRail(addDollars = {}),
        InMemoryCollectionFundingStore(),
        tokenCoordinator,
        CollectionTopUpLocks(),
    )

    private var balance = Fiat(100.0)
    private var nothingToPay = 0
    private var paid = 0
    private val ready = ShareToPay.Ready(
        request = mockk(relaxed = true),
        amount = Fiat(50.0),
        intentId = mockk(relaxed = true),
    )

    @Before
    fun setUp() {
        BottomBarManager.clear()
        every { userManager.accountId } returns listOf<Byte>(1)
        every { userManager.accountCluster } returns owner
        every { owner.withTimelockForToken(any()) } returns owner
        every { payer.observeSelectedToken() } returns flowOf(token)
        coEvery { payer.mayProceed(any(), any(), any()) } returns true
        every { exchange.rateFor(any()) } returns Rate.oneToOne
        every { tokenCoordinator.balanceForToken(any<Token>()) } answers { balance }
        every { transactionController.limits } returns MutableStateFlow(null)
        coEvery { payments.prepare(any(), any()) } returns Result.success(ready)
        // A relaxed Result<Unit> holds a bare Object, and onSuccess's cast throws in the flow's scope.
        coEvery { payments.pay(any(), any(), any(), any()) } returns Result.success(Unit)
    }

    @After
    fun tearDown() {
        scope.cancel()
        BottomBarManager.clear()
    }

    private fun flow() = RequestPayFlow(
        scope = scope,
        payments = payments,
        topUp = topUp,
        payer = payer,
        tokenCoordinator = tokenCoordinator,
        transactionController = transactionController,
        exchange = exchange,
        userManager = userManager,
        resources = resources,
        analytics = analytics,
        openScreen = {},
        onNothingToPay = { nothingToPay++ },
        onPaid = { paid++ },
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

        assertEquals(0, analytics.events.size)
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

    @Test
    fun `no giveable balance stops Pay at the gate and never opens the sheet`() {
        coEvery { payer.mayProceed(any(), any(), any()) } returns false
        val f = flow()

        f.start(listOf<Byte>(2))

        assertEquals(false, f.state.value.open)
        assertEquals(false, f.state.value.preparing)
        coVerify { payer.mayProceed(any(), any(), R.string.description_noBalanceYetToPayRequest) }
    }

    @Test
    fun `a closed request with no giveable balance shows Request Closed, not the gate`() {
        coEvery { payer.mayProceed(any(), any(), any()) } returns false
        coEvery { payments.prepare(any(), any()) } returns Result.success(ShareToPay.Closed(mockk(relaxed = true)))
        val f = flow()

        f.start(listOf<Byte>(2))

        assertEquals(false, f.state.value.open)
        assertEquals(1, BottomBarManager.messages.value.size)
        coVerify(exactly = 0) { payer.mayProceed(any(), any(), any()) }
    }

    @Test
    fun `the swipe with no selected token presents the insufficient balance modal instead of returning silently`() {
        every { payer.observeSelectedToken() } returns flowOf()
        val f = flow()
        f.start(listOf<Byte>(2))

        f.confirm()

        verify(exactly = 1) { payer.presentInsufficientBalance(any(), any()) }
        coVerify(exactly = 0) { payments.pay(any(), any(), any()) }
    }

    @Test
    fun `the sheet keeps its share through the success hold`() {
        coEvery { payments.pay(any(), any(), any()) } returns Result.success(Unit)
        val f = flow()
        f.start(listOf<Byte>(2))

        f.confirm()

        // The hold is running: the request may already read paid, but the sheet still has its share.
        val state = f.state.value
        assertEquals(true, state.open)
        assertEquals(true, state.progress.success)
        assertEquals(Fiat(50.0), state.amount)
    }

    @Test
    fun `a landed payment reports it, and a failed one does not`() {
        coEvery { payments.pay(any(), any(), any()) } returns Result.failure(IllegalStateException("boom"))
        flow().apply { start(listOf<Byte>(2)); confirm() }
        assertEquals(0, paid)

        coEvery { payments.pay(any(), any(), any()) } returns Result.success(Unit)
        flow().apply { start(listOf<Byte>(2)); confirm() }
        assertEquals(1, paid)
    }

    @Test
    fun `dismissing clears the share`() {
        val f = flow()
        f.start(listOf<Byte>(2))

        f.dismiss()

        assertEquals(null, f.state.value.amount)
        assertEquals(null, f.state.value.creator)
    }

    // --- Group Money: a collection funded by conversion (group-collections spec, "Funding a payment") ---

    private val jeffy = mockk<Token>(relaxed = true) { every { this@mockk.address } returns Mint(List(32) { 7 }) }
    private val walletTokens = MutableStateFlow(listOf(Token.usdf, jeffy))
    private val lastPaid = MutableStateFlow<Mint?>(null)
    private val collection = ShareToPay.Ready(
        request = mockk(relaxed = true) { every { this@mockk.collection } returns mockk(relaxed = true) },
        amount = Fiat(10.0),
        intentId = mockk(relaxed = true),
    )

    private fun plan(payable: Boolean) = mockk<CollectionFundingPlan>(relaxed = true) {
        every { payableWithoutTopUp } returns payable
    }

    private fun fundingFlow(): RequestPayFlow {
        every { tokenCoordinator.tokens } returns walletTokens
        every { tokenCoordinator.tokenBalances } returns flowOf(emptyList())
        every { payments.lastPaidCollectionCurrency() } returns lastPaid
        coEvery { payments.prepare(any(), any()) } returns Result.success(collection)
        return flow().also { f -> scope.launch { f.plan.collect {} } }
    }

    @Test
    fun `a short collection that conversions cover goes through the swipe and is paid with funding`() {
        balance = Fiat(2.0)
        every { payments.planCollection(any(), any()) } returns plan(payable = true)
        val f = fundingFlow()
        f.start(listOf<Byte>(2), fund = true)

        f.confirm()

        assertEquals(true, f.state.value.funding)
        verify(exactly = 0) { payer.presentInsufficientBalance(any(), any()) }
        coVerify(exactly = 1) { payments.pay(collection, Token.usdf, owner, true) }
    }

    @Test
    fun `a short collection that conversions can't cover draws the short state, not the old modal, and pays nothing`() {
        balance = Fiat(2.0)
        every { payments.planCollection(any(), any()) } returns plan(payable = false)
        val f = fundingFlow()
        f.start(listOf<Byte>(2), fund = true)

        f.confirm()

        assertEquals(true, f.state.value.open)
        verify(exactly = 0) { payer.presentInsufficientBalance(any(), any()) }
        coVerify(exactly = 0) { payments.pay(any(), any(), any(), any()) }
    }

    @Test
    fun `with Group Money off a short collection is blocked as before and nothing is planned`() {
        balance = Fiat(2.0)
        val f = fundingFlow()
        f.start(listOf<Byte>(2), fund = false)

        f.confirm()

        assertEquals(false, f.state.value.funding)
        verify(exactly = 1) { payer.presentInsufficientBalance(any(), any()) }
        verify(exactly = 0) { payments.planCollection(any(), any()) }
        coVerify(exactly = 0) { payments.pay(any(), any(), any(), any()) }
    }

    @Test
    fun `a covered collection with Group Money off pays from the selected token without funding`() {
        val f = fundingFlow()
        f.start(listOf<Byte>(2), fund = false)

        f.confirm()

        coVerify(exactly = 1) { payments.pay(collection, token, owner, false) }
    }

    @Test
    fun `a split is never funded, even with Group Money on`() {
        every { ready.request.collection } returns null
        coEvery { payments.prepare(any(), any()) } returns Result.success(ready)
        val f = flow()
        f.start(listOf<Byte>(2), fund = true)

        f.confirm()

        assertEquals(false, f.state.value.funding)
        coVerify(exactly = 1) { payments.pay(ready, token, owner, false) }
    }

    @Test
    fun `funding falling short after the swipe replans into the short state without a refetch`() {
        every { payments.planCollection(any(), any()) } returns plan(payable = true)
        coEvery { payments.pay(any(), any(), any(), any()) } returns
            Result.failure(CollectionFundingError.Short(plan(payable = false)))
        val f = fundingFlow()
        f.start(listOf<Byte>(2), fund = true)

        f.confirm()

        assertEquals(true, f.state.value.open)
        assertEquals(true, f.state.value.progress.isIdle)
        verify(exactly = 0) { payer.presentInsufficientBalance(any(), any()) }
        coVerify(exactly = 1) { tokenCoordinator.update() }
        coVerify(exactly = 1) { payments.prepare(any(), any()) }
        assertEquals(0, analytics.events.size)
    }

    @Test
    fun `a conversion still pending says so and keeps the sheet up for a retry`() {
        every { resources.getString(any<Int>()) } answers { "string ${firstArg<Int>()}" }
        every { payments.planCollection(any(), any()) } returns plan(payable = true)
        coEvery { payments.pay(any(), any(), any(), any()) } returns
            Result.failure(CollectionFundingError.ConversionPending())
        val f = fundingFlow()
        f.start(listOf<Byte>(2), fund = true)

        f.confirm()

        assertEquals(true, f.state.value.open)
        assertEquals(true, f.state.value.progress.isIdle)
        val shown = BottomBarManager.messages.value.single()
        assertEquals("string ${R.string.error_title_collectionConversionPending}", shown.title)
        assertEquals("string ${R.string.error_description_collectionConversionPending}", shown.subtitle)
    }

    @Test
    fun `a first collection payment defaults to Dollars`() {
        val f = fundingFlow()
        f.start(listOf<Byte>(2), fund = true)

        assertEquals(Token.usdf, f.token.value)
    }

    @Test
    fun `a later collection defaults to the currency last paid in`() {
        lastPaid.value = jeffy.address
        val f = fundingFlow()
        f.start(listOf<Byte>(2), fund = true)

        assertEquals(jeffy, f.token.value)
    }

    @Test
    fun `the collection currency is shown even with no balance in it`() {
        // Jeffy is known to the wallet but has no balance: tokenBalances is empty.
        lastPaid.value = jeffy.address
        val f = fundingFlow()
        f.start(listOf<Byte>(2), fund = true)

        assertEquals(jeffy, f.token.value)
        assertEquals(Fiat(10.0), f.state.value.amount)
    }

    @Test
    fun `a collection ignores the wallet's selected token`() {
        val f = fundingFlow()
        f.start(listOf<Byte>(2), fund = true)

        assertEquals(Token.usdf, f.token.value)
        f.confirm()
        coVerify(exactly = 0) { payments.pay(any(), token, any(), any()) }
    }

    @Test
    fun `picking another currency replans and starts no payment or purchase`() {
        val dollarsPlan = plan(payable = true)
        val jeffyPlan = plan(payable = true)
        every { payments.planCollection(any(), Token.usdf) } returns dollarsPlan
        every { payments.planCollection(any(), jeffy) } returns jeffyPlan
        val f = fundingFlow()
        f.start(listOf<Byte>(2), fund = true)
        assertEquals(dollarsPlan, f.plan.value)

        f.pickCollectionCurrency(jeffy.address)

        assertEquals(jeffy, f.token.value)
        assertEquals(jeffyPlan, f.plan.value)
        coVerify(exactly = 0) { payments.pay(any(), any(), any(), any()) }
        coVerify(exactly = 0) { tokenCoordinator.selectToken(any()) }
        assertEquals(null, lastPaid.value)
    }

    @Test
    fun `the fee line and Payment Details name each currency as the wallet does`() {
        // Frames 5.2/5.3: $10.00 from $8.00 Dollars and $6.00 Jeffy at a 1% sell fee.
        val dollarsId = Token.usdf.address.base58()
        val jeffyId = jeffy.address.base58()
        every { jeffy.name } returns "Jeffy"
        every { resources.getString(R.string.displayName_dollars) } returns "Dollars"
        every { resources.getString(R.string.label_topUpSourceGooglePay) } returns "Google Pay"
        // CollectionFundingVectorsTest renders these from the real resources; here they only pass through.
        every { resources.getString(R.string.label_collectionFeeApprox, *anyVararg()) } answers { "~${formatArg()}" }
        every { resources.getString(R.string.label_collectionPlusConversionFees, *anyVararg()) } answers {
            "plus ${formatArg()} of conversion fees"
        }
        // The reserve is never cached as a wallet token; the flow knows it anyway.
        every { tokenCoordinator.cachedTokens() } returns listOf(jeffy)
        every { payments.planCollection(any(), any()) } returns planCollectionFunding(
            amountMicros = 10_000_000,
            minorDigits = 2,
            minTopUpMicros = 5_000_000,
            dollarsId = dollarsId,
            selected = dollarsId,
            lastPaid = null,
            balances = listOf(FundingBalance(dollarsId, 8_000_000, 0), FundingBalance(jeffyId, 6_000_000, 100)),
        )
        val f = fundingFlow()
        scope.launch { f.fundingUi.collect {} }
        f.start(listOf<Byte>(2), fund = true)

        val ui = assertNotNull(f.fundingUi.value)
        assertEquals("plus ~$0.02 of conversion fees", ui.modalFee)
        assertEquals("Dollars", ui.details.pay.row.name)
        assertEquals(Token.usdf, ui.details.pay.token)
        assertEquals(listOf("Dollars", "Jeffy"), ui.details.use.map { it.row.name })
        assertEquals(listOf(Token.usdf, jeffy), ui.details.use.map { it.token })
    }

    @Test
    fun `a pick lasts only as long as the sheet`() {
        val f = fundingFlow()
        f.start(listOf<Byte>(2), fund = true)
        f.pickCollectionCurrency(jeffy.address)

        f.dismiss()
        f.start(listOf<Byte>(2), fund = true)

        assertEquals(Token.usdf, f.token.value)
    }

    @Test
    fun `the send limit still blocks a funded collection`() {
        every { payments.planCollection(any(), any()) } returns plan(payable = true)
        every { transactionController.limits } returns MutableStateFlow(
            mockk(relaxed = true) { every { sendLimitFor(any()) } returns mockk(relaxed = true) { every { nextTransaction } returns 5.0 } },
        )
        val f = fundingFlow()
        f.start(listOf<Byte>(2), fund = true)

        f.confirm()

        coVerify(exactly = 0) { payments.pay(any(), any(), any(), any()) }
    }

    /** The first format argument a mocked [ResourceHelper.getString] was called with. */
    private fun MockKAnswerScope<String, String>.formatArg(): Any? = (args[1] as Array<*>)[0]
}
