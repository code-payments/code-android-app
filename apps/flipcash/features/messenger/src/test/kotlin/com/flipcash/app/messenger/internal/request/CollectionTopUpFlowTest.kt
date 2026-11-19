package com.flipcash.app.messenger.internal.request

import com.flipcash.app.analytics.RecordingAnalytics
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.messenger.internal.payment.StartChattingPayer
import com.flipcash.app.tokens.FundingBalance
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.app.tokens.planCollectionFunding
import com.flipcash.services.user.UserManager
import com.flipcash.shared.payments.request.CollectionFundingPlanner
import com.flipcash.shared.payments.request.CollectionTopUp
import com.flipcash.shared.payments.request.CollectionTopUpBlocked
import com.flipcash.shared.payments.request.CollectionTopUpLocks
import com.flipcash.shared.payments.request.PaymentRequestPaymentDelegate
import com.flipcash.shared.payments.request.ShareToPay
import com.flipcash.shared.payments.request.TopUpBlock
import com.flipcash.shared.payments.request.TopUpPayment
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Limits
import com.getcode.opencode.model.financial.SendLimit
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.usdf
import com.getcode.solana.keys.Mint
import com.getcode.util.resources.ResourceHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S5's Add and Pay from the open collection sheet (group-collections spec, "Adding money and
 * recovery"), run against the shared vectors' cases. Plans are the real planner's over balances
 * the test holds, so a delivered top-up changes the replan. Coinbase and Google Pay are
 * [FakeCollectionTopUpRail]; the store outlives each flow, as DataStore outlives the process.
 * Robolectric only for org.json.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CollectionTopUpFlowTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val fixture: JSONObject = File(FIXTURE).let {
        assertTrue(it.exists(), "missing ${it.absolutePath}")
        JSONObject(it.readText())
    }

    private val payments = mockk<PaymentRequestPaymentDelegate>(relaxed = true)
    private val payer = mockk<StartChattingPayer>(relaxed = true)
    private val tokenCoordinator = mockk<TokenCoordinator>(relaxed = true)
    private val exchange = mockk<Exchange>(relaxed = true)
    private val userManager = mockk<UserManager>(relaxed = true)
    private val transactionController = mockk<TransactionController>(relaxed = true)
    private val resources = mockk<ResourceHelper>(relaxed = true)
    private val owner = mockk<AccountCluster>(relaxed = true)
    private val scopes = mutableListOf<CoroutineScope>()

    /** The device's store and Coinbase: both outlive a flow and a process. */
    private val store = InMemoryCollectionFundingStore()
    private var dollarsMicros = 0L
    private val rail = FakeCollectionTopUpRail(addDollars = { dollarsMicros += it })

    private lateinit var input: JSONObject
    private lateinit var request: ShareToPay.Ready
    private var paid = 0
    private var nothingToPay = 0
    private var payFailures = 0

    @Before
    fun setUp() {
        BottomBarManager.clear()
        every { userManager.accountId } returns listOf<Byte>(1)
        every { userManager.accountCluster } returns owner
        every { owner.withTimelockForToken(any()) } returns owner
        every { payer.observeSelectedToken() } returns flowOf(Token.usdf)
        every { exchange.rateFor(any()) } returns Rate.oneToOne
        every { transactionController.limits } returns MutableStateFlow(null)
        every { tokenCoordinator.tokens } returns MutableStateFlow(listOf(Token.usdf))
        every { tokenCoordinator.tokenBalances } returns flowOf(emptyList())
        every { tokenCoordinator.currentBalance(Mint.usdf) } answers { Fiat(quarks = dollarsMicros, currencyCode = CurrencyCode.USD) }
        every { payments.lastPaidCollectionCurrency() } returns MutableStateFlow(null)
        every { payments.planCollection(any(), any()) } answers { planNow() }
        val planner = CollectionFundingPlanner(tokenCoordinator, exchange)
        every { payments.collectionTopUpCents(any(), any()) } answers { planner.topUpUsdCents(firstArg(), Rate.oneToOne) }
        // GetPaymentIntentId: ALREADY_PAID once the share is paid.
        coEvery { payments.prepare(any(), any()) } answers {
            Result.success(if (paid > 0) ShareToPay.NothingToPay(request.request) else request)
        }
        coEvery { payments.pay(any(), any(), any(), any()) } answers {
            if (payFailures > 0) {
                payFailures--
                Result.failure(IllegalStateException("submit failed"))
            } else {
                Result.success(Unit)
            }
        }
    }

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
        BottomBarManager.clear()
    }

    /** Loads a vectors case: its amount, and its balances with Dollars held by the test. */
    private fun useCase(name: String): JSONObject {
        val case = fixture.getJSONArray("cases").objects().first { it.getString("name") == name }
        input = case.getJSONObject("input")
        dollarsMicros = input.getJSONArray("balances").objects()
            .firstOrNull { it.getString("id") == DOLLARS }?.getLong("fiat_micros") ?: 0L
        request = ShareToPay.Ready(
            request = mockk(relaxed = true) {
                every { id } returns REQUEST_ID
                every { collection } returns mockk(relaxed = true)
            },
            amount = Fiat(quarks = input.getLong("amount_micros"), currencyCode = CurrencyCode.USD),
            intentId = mockk(relaxed = true),
        )
        return case
    }

    /** The case's plan over the balances as they now stand. */
    private fun planNow() = planCollectionFunding(
        amountMicros = input.getLong("amount_micros"),
        minorDigits = input.getInt("minor_digits"),
        minTopUpMicros = input.getLong("min_top_up_micros"),
        dollarsId = DOLLARS,
        selected = input.optString("selected").takeIf { !input.isNull("selected") },
        lastPaid = input.optString("last_paid").takeIf { !input.isNull("last_paid") },
        balances = input.getJSONArray("balances").objects()
            .map { FundingBalance(it.getString("id"), it.getLong("fiat_micros"), it.getInt("sell_fee_bps")) }
            .filterNot { it.id == DOLLARS } + FundingBalance(DOLLARS, dollarsMicros, 0),
    )

    /** A pay flow on its own scope; a new [CollectionTopUp] and locks stand for a new process. */
    private fun flow(topUp: CollectionTopUp = CollectionTopUp(rail, store, tokenCoordinator, CollectionTopUpLocks())): RequestPayFlow {
        val scope = CoroutineScope(Dispatchers.Unconfined).also(scopes::add)
        return RequestPayFlow(
            scope = scope,
            payments = payments,
            topUp = topUp,
            payer = payer,
            tokenCoordinator = tokenCoordinator,
            transactionController = transactionController,
            exchange = exchange,
            userManager = userManager,
            resources = resources,
            analytics = RecordingAnalytics(),
            openScreen = {},
            onNothingToPay = { nothingToPay++ },
            onPaid = { paid++ },
        ).also { f -> scope.launch { f.token.collect {} } }
    }

    private fun openSheet(f: RequestPayFlow = flow()): RequestPayFlow = f.also {
        it.start(REQUEST_ID, fund = true)
        assertTrue(it.state.value.open)
    }

    private fun expectedCents(case: JSONObject): Long =
        case.getJSONObject("expected").getLong("top_up_micros") / MICROS_PER_CENT

    /**
     * Frames 1, 2, 4, 7 and 8: Add and Pay orders the case's top-up to the cent ($10.00, $5.00,
     * $5.00, $5.00, $12.04), and the payment runs once its Dollars land, with no second tap.
     */
    @Test
    fun `each section's Add and Pay orders its exact cents and pays once when the Dollars land`() {
        for (name in SECTIONS) {
            setUp()
            rail.orders.clear()
            paid = 0
            val case = useCase(name)
            assertEquals(false, planNow().payableWithoutTopUp, name)
            val f = openSheet()

            f.onAddAndPay()

            assertEquals(listOf(expectedCents(case)), rail.orders, name)
            assertEquals(listOf(true), rail.gateChecks.takeLast(1), name)
            assertEquals(1, paid, name)
            assertTrue(store.topUps.isEmpty(), name)
            rail.gateChecks.clear()
        }
        coVerify(exactly = SECTIONS.size) { payments.pay(any(), Token.usdf, owner, true) }
    }

    @Test
    fun `Finishing Payment shows from Google Pay closing until it's paid`() {
        useCase("s8-short-over-min")
        rail.delivery = CompletableDeferred()
        val f = openSheet()

        f.onAddAndPay()
        assertTrue(f.state.value.finishing)
        assertEquals(0, paid)

        rail.delivery!!.complete(Unit)
        assertEquals(1, paid)
        assertEquals(listOf(1204L), rail.orders)
    }

    /** Recovery 1: killed with Google Pay open, the relaunch waits on the kept top-up and pays once. */
    @Test
    fun `process death between Google Pay and pay - the relaunch waits on the top-up, pays once and orders nothing`() {
        useCase("s7-short-under-min")
        rail.googlePay = CompletableDeferred()
        flow().let(::openSheet).onAddAndPay()
        assertEquals(listOf(500L), rail.orders)
        assertTrue(store.topUps.isNotEmpty(), "kept before Google Pay opened")

        // The process dies; Coinbase still has the authorized order.
        scopes.forEach { it.cancel() }
        assertEquals(0, paid)

        val relaunched = openSheet()

        assertEquals(listOf(500L), rail.orders)
        assertEquals(1, paid)
        assertTrue(store.topUps.isEmpty())
        coVerify(exactly = 1) { payments.pay(any(), any(), any(), any()) }
        assertTrue(relaunched.state.value.open)
    }

    /** Recovery 2: a second tap, here and on another screen, waits on the first top-up. */
    @Test
    fun `a second tap while Google Pay is open orders nothing, here or on another screen`() {
        useCase("s4-add-then-pay")
        rail.googlePay = CompletableDeferred()
        val topUp = CollectionTopUp(rail, store, tokenCoordinator, CollectionTopUpLocks())
        val chat = openSheet(flow(topUp))
        chat.onAddAndPay()
        chat.onAddAndPay()

        val details = openSheet(flow(topUp))
        assertTrue(details.state.value.finishing, "the other screen waits on the kept top-up")
        details.onAddAndPay()

        rail.googlePay!!.complete(Unit)

        assertEquals(listOf(500L), rail.orders)
        assertEquals(1, paid)
        coVerify(exactly = 1) { payments.pay(any(), any(), any(), any()) }
        assertEquals(1, nothingToPay, "the other screen refetches and finds it paid")
    }

    /** Recovery 3: the Dollars stay when the payment fails, and the retry charges nothing. */
    @Test
    fun `a payment failing after the top-up keeps the Dollars and the retry charges nothing`() {
        useCase("s7-short-under-min")
        payFailures = 1
        val f = openSheet()

        f.onAddAndPay()
        assertEquals(0, paid)
        assertEquals(11_000_000L, dollarsMicros, "the $5.00 stays in Dollars")
        assertTrue(planNow().payableWithoutTopUp)

        f.confirm()

        assertEquals(1, paid)
        assertEquals(listOf(500L), rail.orders)
        coVerify(exactly = 2) { payments.pay(any(), any(), any(), any()) }
    }

    @Test
    fun `a retry through Add and Pay after the top-up pays from the Dollars and orders nothing`() {
        useCase("s7-short-under-min")
        payFailures = 1
        val f = openSheet()
        f.onAddAndPay()

        f.onAddAndPay()

        assertEquals(1, paid)
        assertEquals(listOf(500L), rail.orders)
    }

    /** Recovery 4: a short delivery replans from the balances and asks before any further top-up. */
    @Test
    fun `a short delivery shows the short state again and asks before a further top-up`() {
        useCase("s8-short-over-min")
        rail.deliverMicros = 5_000_000L
        val f = openSheet()

        f.onAddAndPay()

        assertEquals(listOf(1204L), rail.orders)
        assertEquals(0, paid)
        assertTrue(store.topUps.isEmpty())
        assertTrue(f.state.value.open)
        assertTrue(f.state.value.progress.isIdle)
        assertEquals(false, f.state.value.finishing)
        val replan = planNow()
        assertEquals(false, replan.payableWithoutTopUp)
        assertEquals(1, rail.orders.size, "nothing more is ordered without a tap")

        rail.deliverMicros = null
        f.onAddAndPay()

        assertEquals(listOf(1204L, replan.topUpMicros / MICROS_PER_CENT), rail.orders)
        assertEquals(1, paid)
    }

    /** Recovery 5: GetPaymentIntentId runs again after the top-up, and ALREADY_PAID stops the pay. */
    @Test
    fun `paid by someone else while the top-up landed - the Dollars stay and nothing is paid`() {
        useCase("s4-add-then-pay")
        rail.delivery = CompletableDeferred()
        val f = openSheet()
        f.onAddAndPay()
        // Another device paid this share meanwhile.
        coEvery { payments.prepare(any(), any()) } returns Result.success(ShareToPay.NothingToPay(request.request))

        rail.delivery!!.complete(Unit)

        assertEquals(0, paid)
        assertEquals(1, nothingToPay)
        assertEquals(11_000_000L, dollarsMicros)
        coVerify(exactly = 0) { payments.pay(any(), any(), any(), any()) }
        assertEquals(false, f.state.value.open)
    }

    @Test
    fun `the send limit is checked before ordering`() {
        useCase("s8-short-over-min")
        val limit = mockk<SendLimit>(relaxed = true) { every { nextTransaction } returns 1.0 }
        every { transactionController.limits } returns MutableStateFlow<Limits?>(
            mockk<Limits>(relaxed = true) { every { sendLimitFor(any()) } returns limit },
        )
        val f = openSheet()

        f.onAddAndPay()

        assertTrue(rail.orders.isEmpty())
        assertTrue(store.topUps.isEmpty())
    }

    @Test
    fun `a Google Pay gate keeps nothing and orders nothing`() {
        useCase("s4-add-then-pay")
        rail.orderFailure = CollectionTopUpBlocked(TopUpBlock.GooglePayNotSupported)
        val f = openSheet()

        f.onAddAndPay()

        assertTrue(rail.orders.isEmpty())
        assertTrue(store.topUps.isEmpty())
        assertTrue(f.state.value.progress.isIdle)
        assertEquals(0, paid)
    }

    @Test
    fun `closing Google Pay before paying keeps nothing, and the next tap orders again`() {
        useCase("s4-add-then-pay")
        rail.payment = TopUpPayment.NotCharged
        val f = openSheet()

        f.onAddAndPay()
        assertTrue(store.topUps.isEmpty())
        assertTrue(f.state.value.progress.isIdle)
        assertEquals(0, paid)

        rail.payment = TopUpPayment.Succeeded
        f.onAddAndPay()

        assertEquals(listOf(500L, 500L), rail.orders)
        assertEquals(1, paid)
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map(::getJSONObject)

    private companion object {
        const val FIXTURE = "../../shared/tokens/src/androidHostTest/resources/collection_funding.json"
        const val DOLLARS = "dollars"
        val REQUEST_ID = listOf<Byte>(2)
        const val MICROS_PER_CENT = 10_000L
        val SECTIONS = listOf("s1-no-balance", "s2-min-top-up", "s4-add-then-pay", "s7-short-under-min", "s8-short-over-min")
    }
}
