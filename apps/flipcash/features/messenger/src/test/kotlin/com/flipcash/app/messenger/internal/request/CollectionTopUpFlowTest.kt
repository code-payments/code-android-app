package com.flipcash.app.messenger.internal.request

import com.flipcash.app.analytics.RecordingAnalytics
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.messenger.internal.payment.StartChattingPayer
import com.flipcash.app.tokens.FundingBalance
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.app.tokens.planCollectionFunding
import com.flipcash.features.messenger.R
import com.flipcash.services.user.UserManager
import com.flipcash.shared.payments.request.CollectionFundingError
import com.flipcash.shared.payments.request.CollectionFundingPlanner
import com.flipcash.shared.payments.request.CollectionTopUp
import com.flipcash.shared.payments.request.CollectionTopUpBlocked
import com.flipcash.shared.payments.request.CollectionTopUpLocks
import com.flipcash.shared.payments.request.PaymentNotConfirmed
import com.flipcash.shared.payments.request.PaymentRequestPaymentDelegate
import com.flipcash.shared.payments.request.ShareToPay
import com.flipcash.shared.payments.request.TopUpBlock
import com.flipcash.shared.payments.request.TopUpDelivery
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
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
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
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

    /** Every screen the flow opened; Finishing Payment is a sheet state and opens none. */
    private val screens = mutableListOf<AppRoute>()
    private val verifications = mutableListOf<AppRoute.Verification>()

    /** How many top-ups had been ordered at each settle of the kept conversions. */
    private val settles = mutableListOf<Int>()

    @Before
    fun setUp() {
        BottomBarManager.clear()
        screens.clear()
        verifications.clear()
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
        // Nothing kept from an earlier attempt unless a test keeps a conversion.
        settles.clear()
        coEvery { payments.settleKeptConversions(any(), any()) } returns Result.success(false)
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

    /** The case's plan for [amountMicros], by default its own amount, over the balances as they now stand. */
    private fun planNow(amountMicros: Long = input.getLong("amount_micros")) = planCollectionFunding(
        amountMicros = amountMicros,
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
            openScreen = { screens += it },
            verify = { verifications += it },
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

    /** Recovery 3 and 5: the failed payment refetches before any retry, and ALREADY_PAID stops it. */
    @Test
    fun `a payment failing after the top-up refetches first, and ALREADY_PAID stops the retry`() {
        useCase("s7-short-under-min")
        var failed = false
        coEvery { payments.pay(any(), any(), any(), any()) } answers {
            failed = true
            Result.failure(IllegalStateException("submit failed"))
        }
        // The submit landed after all, or someone else paid: the refetch reports ALREADY_PAID.
        coEvery { payments.prepare(any(), any()) } answers {
            Result.success(if (failed) ShareToPay.NothingToPay(request.request) else request)
        }
        val f = openSheet()

        f.onAddAndPay()

        coVerifyOrder {
            payments.pay(any(), any(), any(), any())
            payments.prepare(any(), any())
        }
        coVerify(exactly = 1) { payments.pay(any(), any(), any(), any()) }
        assertEquals(listOf(500L), rail.orders)
        assertEquals(11_000_000L, dollarsMicros, "the $5.00 stays in Dollars")
        assertEquals(false, f.state.value.open)
    }

    /**
     * Pay step 4: Finishing Payment ends when the payment after the Dollars land isn't confirmed,
     * even while the server stays silent, and the next tap asks first and stops if it landed.
     */
    @Test
    fun `a payment not confirmed after the Dollars land ends Finishing Payment, and the next tap stops if it landed`() {
        every { resources.getString(any<Int>()) } answers { "string ${firstArg<Int>()}" }
        useCase("s8-short-over-min")
        rail.delivery = CompletableDeferred()
        var silent = false
        var landed = false
        coEvery { payments.pay(any(), any(), any(), any()) } answers {
            silent = true
            Result.failure(PaymentNotConfirmed())
        }
        coEvery { payments.prepare(any(), any()) } coAnswers {
            if (silent) awaitCancellation()
            Result.success(if (landed) ShareToPay.NothingToPay(request.request) else request)
        }
        val f = openSheet()
        f.onAddAndPay()
        assertTrue(f.state.value.finishing)

        rail.delivery!!.complete(Unit)

        assertEquals(false, f.state.value.finishing)
        assertTrue(f.state.value.progress.isIdle, "the sheet can be left")
        val shown = BottomBarManager.messages.value.single()
        assertEquals("string ${R.string.error_title_paymentNotConfirmed}", shown.title)
        assertEquals("string ${R.string.error_description_paymentNotConfirmed}", shown.subtitle)
        coVerify(exactly = 1) { payments.pay(any(), any(), any(), any()) }

        // The payment landed after all, and the server answers again.
        silent = false
        landed = true
        f.confirm()

        coVerify(exactly = 1) { payments.pay(any(), any(), any(), any()) }
        assertEquals(listOf(1204L), rail.orders)
        assertEquals(1, nothingToPay)
        assertEquals(false, f.state.value.open)
    }

    /**
     * A refetch for a top-up isn't the swipe's ask: after a failed payment, a top-up closed in
     * Google Pay leaves the next swipe asking for the intent id before it pays.
     */
    @Test
    fun `after a failed payment, a top-up closed in Google Pay still leaves the next swipe asking first`() {
        useCase("s7-short-under-min")
        payFailures = 1
        var asks = 0
        coEvery { payments.prepare(any(), any()) } answers {
            asks++
            Result.success(request)
        }
        val f = openSheet()
        f.onAddAndPay()
        assertEquals(0, paid)

        // The Dollars went elsewhere, so the next tap tops up again, and Google Pay is closed.
        dollarsMicros -= 5_000_000
        rail.payment = TopUpPayment.NotCharged
        f.onAddAndPay()
        assertEquals(listOf(500L, 500L), rail.orders)
        assertTrue(f.state.value.progress.isIdle)

        // Dollars back: the swipe pays, and asks first.
        dollarsMicros += 5_000_000
        val before = asks
        f.confirm()

        assertEquals(before + 1, asks)
        assertEquals(1, paid)
        coVerify(exactly = 2) { payments.pay(any(), any(), any(), any()) }
    }

    /**
     * A swipe the balances no longer cover shows the short state; its balance refresh runs outside
     * the request's lock, so a server that doesn't answer it can't hold the next tap.
     */
    @Test
    fun `a short swipe whose balance refresh never answers leaves Add and Pay free to run`() {
        useCase("s7-short-under-min")
        dollarsMicros += 5_000_000
        var refreshHangs = false
        coEvery { tokenCoordinator.update() } coAnswers {
            if (refreshHangs) {
                refreshHangs = false
                awaitCancellation()
            }
        }
        var shortOnce = true
        coEvery { payments.pay(any(), any(), any(), any()) } answers {
            if (shortOnce) {
                shortOnce = false
                // The Dollars went elsewhere after the sheet planned.
                dollarsMicros -= 5_000_000
                refreshHangs = true
                Result.failure(CollectionFundingError.Short(planNow()))
            } else {
                Result.success(Unit)
            }
        }
        val f = openSheet()
        f.confirm()
        assertTrue(f.state.value.progress.isIdle)
        assertEquals(0, paid)

        f.onAddAndPay()

        assertEquals(listOf(500L), rail.orders)
        assertEquals(1, paid)
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

    /**
     * Coinbase asks for a verified phone or email before the order. The payment waits through the
     * check: the sheet hides while it is up, comes back once it passes, and Add and Pay runs again
     * without a second tap.
     */
    @Test
    fun `a verification Coinbase asks for brings the sheet back and pays once it passes`() {
        useCase("s4-add-then-pay")
        rail.orderFailure = CollectionTopUpBlocked(TopUpBlock.Verification(phone = true, email = true))
        val f = openSheet()

        f.onAddAndPay()

        assertEquals(1, verifications.size)
        assertTrue(verifications.single().includePhone && verifications.single().includeEmail)
        assertTrue(screens.isEmpty(), "verification opens for a result, not as a plain screen")
        assertEquals(false, f.state.value.open, "the sheet hides while verification is up")
        assertTrue(f.state.value.funding, "the payment is kept")
        assertEquals(0, paid)

        rail.orderFailure = null
        f.onVerified(verified = true)

        assertEquals(listOf(500L), rail.orders)
        assertEquals(1, paid)
        assertTrue(f.state.value.open && f.state.value.progress.success, "the sheet holds the checkmark")
    }

    @Test
    fun `backing out of a verification Coinbase asks for closes the sheet and orders nothing`() {
        useCase("s4-add-then-pay")
        rail.orderFailure = CollectionTopUpBlocked(TopUpBlock.Verification(phone = true, email = false))
        val f = openSheet()
        f.onAddAndPay()
        rail.orderFailure = null

        f.onVerified(verified = false)

        assertTrue(rail.orders.isEmpty())
        assertEquals(0, paid)
        assertEquals(false, f.state.value.open)
        assertEquals(false, f.state.value.funding)

        // A result arriving late for a sheet that has gone does nothing.
        f.onVerified(verified = true)
        assertTrue(rail.orders.isEmpty())
    }

    @Test
    fun `a verification the Request screen's Pay ran into resumes its top-up once it passes`() {
        val case = useCase("s1-no-balance")
        rail.orderFailure = CollectionTopUpBlocked(TopUpBlock.Verification(phone = false, email = true))
        val f = flow()

        tapRequestScreenPay(f)

        assertEquals(1, verifications.size)
        assertEquals(false, f.state.value.open)
        assertEquals(0, paid)

        rail.orderFailure = null
        f.onVerified(verified = true)

        assertEquals(listOf(expectedCents(case)), rail.orders)
        assertEquals(1, paid)
        assertTrue(f.state.value.open && f.state.value.progress.success, "the sheet holds the checkmark")
    }

    /**
     * An earlier attempt kept a conversion into Dollars that lands [micros] once settled; later
     * settles find nothing kept.
     */
    private fun keepConversionLanding(micros: Long) {
        coEvery { payments.settleKeptConversions(any(), any()) } answers {
            settles += rail.orders.size
            if (settles.size == 1) dollarsMicros += micros
            Result.success(settles.size == 1)
        }
    }

    /** Recovery 3: the kept conversion settles before anything is ordered. */
    @Test
    fun `a retry settles a kept conversion before ordering a top-up the conversion leaves unchanged`() {
        useCase("s7-short-under-min")
        // $1.00 more in Dollars still leaves the payment short by less than the $5.00 minimum.
        keepConversionLanding(1_000_000L)
        val f = openSheet()

        f.onAddAndPay()

        assertEquals(listOf(0), settles, "settled once, before any order")
        assertEquals(listOf(500L), rail.orders)
        assertEquals(1, paid)
    }

    /** Recovery 3, outcome 3: the settled conversion covers the payment, so Google Pay never opens. */
    @Test
    fun `a kept conversion that covers the payment once settled pays with no top-up`() {
        useCase("s4-add-then-pay")
        keepConversionLanding(4_000_000L)
        val f = openSheet()

        f.onAddAndPay()

        assertTrue(rail.orders.isEmpty(), "nothing ordered")
        assertEquals(1, paid)
        coVerify(exactly = 1) { payments.pay(any(), Token.usdf, owner, true) }
    }

    /**
     * Recovery 3, outcome 4: the settled conversion changes the top-up from the $12.04 the user saw,
     * so the short state asks again, and the next tap orders the new amount.
     */
    @Test
    fun `a kept conversion that changes the top-up shows the short state, and the next tap orders the new amount`() {
        val case = useCase("s8-short-over-min")
        assertEquals(1204L, expectedCents(case))
        keepConversionLanding(2_000_000L)
        val f = openSheet()
        CoroutineScope(Dispatchers.Unconfined).also(scopes::add).launch { f.plan.collect {} }
        assertEquals(1204L, assertNotNull(f.plan.value).topUpMicros / MICROS_PER_CENT, "the short state before")

        f.onAddAndPay()

        assertTrue(rail.orders.isEmpty(), "nothing ordered at the amount seen before the conversion settled")
        assertEquals(0, paid)
        assertTrue(f.state.value.open)
        assertTrue(f.state.value.progress.isIdle)
        assertEquals(false, f.state.value.finishing)
        val replanned = planNow().topUpMicros / MICROS_PER_CENT
        assertEquals(1004L, replanned)
        assertEquals(replanned, assertNotNull(f.plan.value).topUpMicros / MICROS_PER_CENT, "the short state draws the new top-up")

        f.onAddAndPay()

        assertEquals(listOf(replanned), rail.orders, "sized from the balances after the conversion")
        assertEquals(1, paid)
    }

    /** Outcome 4 from the Request screen, which has no short state: the sheet opens on it. */
    @Test
    fun `a kept conversion that changes the Request screen's top-up opens the sheet's short state`() {
        useCase("s1-no-balance")
        keepConversionLanding(3_000_000L)
        val f = flow()

        tapRequestScreenPay(f)

        assertTrue(rail.orders.isEmpty())
        assertEquals(0, paid)
        assertTrue(f.state.value.open)
        assertEquals(false, f.state.value.direct)
        assertTrue(f.state.value.progress.isIdle)
    }

    /**
     * Outcome 5: a kept conversion still unsettled orders nothing and says so; any other failure to
     * settle orders nothing either.
     */
    @Test
    fun `a kept conversion that won't settle orders nothing and says why`() {
        val outcomes = listOf(
            CollectionFundingError.ConversionPending() to
                (R.string.error_title_collectionConversionPending to R.string.error_description_collectionConversionPending),
            IllegalStateException("offline") to
                (R.string.error_title_cashFailedToSend to R.string.error_description_cashFailedToSend),
        )
        for ((cause, strings) in outcomes) {
            setUp()
            every { resources.getString(any<Int>()) } answers { "string ${firstArg<Int>()}" }
            useCase("s7-short-under-min")
            coEvery { payments.settleKeptConversions(any(), any()) } returns Result.failure(cause)
            val f = openSheet()

            f.onAddAndPay()

            assertTrue(rail.orders.isEmpty(), "$cause")
            assertEquals(0, paid, "$cause")
            coVerify(exactly = 0) { payments.pay(any(), any(), any(), any()) }
            assertTrue(f.state.value.open, "$cause")
            assertTrue(f.state.value.progress.isIdle, "$cause")
            val shown = BottomBarManager.messages.value.single()
            assertEquals("string ${strings.first}", shown.title, "$cause")
            assertEquals("string ${strings.second}", shown.subtitle, "$cause")
        }
    }

    /** The Request screen's Pay, which tops up without the sheet when nothing funds the collection. */
    private fun tapRequestScreenPay(f: RequestPayFlow) = f.start(REQUEST_ID, fund = true, direct = true)

    private fun directPayNow(f: RequestPayFlow): CollectionDirectPayUi? = runBlocking {
        f.directPay(flowOf(request.amount)).first()
    }

    /**
     * Sections 1 and 2: no balance funds it, so the Request screen's button is the top-up. It
     * orders the case's cents ($10.00, $5.00) with Google Pay over the Request screen and no
     * sheet, then pays once the Dollars land, the sheet up from the charge on.
     */
    @Test
    fun `sections 1 and 2 - the Request screen's Pay opens Google Pay with no sheet, then pays in the sheet`() {
        for (name in listOf("s1-no-balance", "s2-min-top-up")) {
            setUp()
            rail.orders.clear()
            paid = 0
            val case = useCase(name)
            rail.googlePay = CompletableDeferred()
            val f = flow()

            tapRequestScreenPay(f)

            assertEquals(listOf(expectedCents(case)), rail.orders, name)
            assertEquals(false, f.state.value.open, "$name: Google Pay opens over the Request screen")
            assertTrue(f.state.value.direct, name)
            assertTrue(f.state.value.progress.loading, "$name: the button spins")

            rail.googlePay!!.complete(Unit)

            assertEquals(1, paid, name)
            assertTrue(f.state.value.open && f.state.value.progress.success, "$name: the sheet holds the checkmark")
            assertTrue(store.topUps.isEmpty(), name)
            assertTrue(screens.isEmpty(), name)
        }
        coVerify(exactly = 2) { payments.pay(any(), Token.usdf, owner, true) }
    }

    /** Sections 4, 7 and 8: a balance funds part of it, so Pay opens the sheet and orders nothing. */
    @Test
    fun `sections 4, 7 and 8 - the Request screen's Pay opens the sheet`() {
        for (name in listOf("s4-add-then-pay", "s7-short-under-min", "s8-short-over-min")) {
            setUp()
            useCase(name)
            val f = flow()

            tapRequestScreenPay(f)

            assertTrue(f.state.value.open, name)
            assertEquals(false, f.state.value.direct, name)
            assertTrue(f.state.value.progress.isIdle, name)
            assertTrue(rail.orders.isEmpty(), name)
            assertEquals(null, directPayNow(f), "$name: the button reads Pay and opens the sheet")
        }
    }

    /** Frames 1.1 and 2.1: "Pay $10.00" when the top-up is the amount, else the Add and Pay label. */
    @Test
    fun `the Request screen's label is Pay for section 1 and Add and Pay for section 2`() {
        useCase("s1-no-balance")
        assertEquals(CollectionDirectPayUi(), directPayNow(flow()))

        useCase("s2-min-top-up")
        val two = directPayNow(flow())
        assertTrue(two?.button != null && two.footnote != null, "section 2 adds the minimum: $two")
    }

    @Test
    fun `a balance that arrived after the label was drawn opens the sheet instead`() {
        useCase("s1-no-balance")
        val f = flow()
        assertEquals(CollectionDirectPayUi(), directPayNow(f))
        // $5.00 lands in Dollars before the tap: it now funds half, so the sheet asks first.
        dollarsMicros = 5_000_000L

        tapRequestScreenPay(f)

        assertTrue(f.state.value.open)
        assertEquals(false, f.state.value.direct)
        assertTrue(rail.orders.isEmpty())
        assertEquals(null, directPayNow(f))
    }

    /** Recovery 2 from the Request screen: taps while Google Pay is up order nothing more. */
    @Test
    fun `taps on the Request screen's Pay while Google Pay is open order nothing more`() {
        useCase("s1-no-balance")
        rail.googlePay = CompletableDeferred()
        val topUp = CollectionTopUp(rail, store, tokenCoordinator, CollectionTopUpLocks())
        val details = flow(topUp)
        tapRequestScreenPay(details)
        tapRequestScreenPay(details)
        details.onAddAndPay()

        // The chat's deck Pay on the same request waits on the kept top-up.
        val chat = openSheet(flow(topUp))
        assertTrue(chat.state.value.finishing)

        rail.googlePay!!.complete(Unit)

        assertEquals(listOf(1000L), rail.orders)
        assertEquals(1, paid)
        coVerify(exactly = 1) { payments.pay(any(), any(), any(), any()) }
    }

    /**
     * "Finishing Payment" is a state of the pay sheet on every entry: the sheet is up and locked
     * in it, and no screen opened for it.
     */
    private fun assertFinishingInTheSheet(f: RequestPayFlow, entry: String) {
        val state = f.state.value
        assertTrue(state.open && state.finishing, "$entry: Finishing Payment in the sheet, was $state")
        assertTrue(state.progress.loading, "$entry: the sheet is locked")
        assertTrue(screens.isEmpty(), "$entry: no screen opened, was $screens")
        assertEquals(0, paid, entry)
    }

    /** Frame 1.3: the Request screen's Pay, with no balance, shows Finishing Payment in the sheet. */
    @Test
    fun `1_3 - the Request screen's Pay shows Finishing Payment in the sheet while the Dollars land`() {
        useCase("s1-no-balance")
        rail.delivery = CompletableDeferred()
        val f = flow()

        tapRequestScreenPay(f)
        assertFinishingInTheSheet(f, "section 1")
        assertTrue(f.state.value.direct, "the Request screen's button started it")

        rail.delivery!!.complete(Unit)
        assertEquals(1, paid)
    }

    /** Section 2: the Request screen's Add and Pay, under the minimum, shows Finishing Payment in the sheet. */
    @Test
    fun `2 - the Request screen's Add and Pay shows Finishing Payment in the sheet while the Dollars land`() {
        useCase("s2-min-top-up")
        rail.delivery = CompletableDeferred()
        val f = flow()

        tapRequestScreenPay(f)
        assertFinishingInTheSheet(f, "section 2")
        assertEquals(listOf(500L), rail.orders)

        rail.delivery!!.complete(Unit)
        assertEquals(1, paid)
    }

    /** Frames 4.4, 7.5 and 8.4: the sheet's own Add and Pay shows Finishing Payment in the same sheet. */
    @Test
    fun `4_4, 7_5 and 8_4 - the sheet's Add and Pay shows Finishing Payment in the same sheet`() {
        for (name in listOf("s4-add-then-pay", "s7-short-under-min", "s8-short-over-min")) {
            setUp()
            paid = 0
            useCase(name)
            rail.delivery = CompletableDeferred()
            val f = flow()
            tapRequestScreenPay(f)
            assertTrue(f.state.value.open, "$name: a balance funds part, so Pay opens the sheet")

            f.onAddAndPay()
            assertFinishingInTheSheet(f, name)

            rail.delivery!!.complete(Unit)
            assertEquals(1, paid, name)
        }
    }

    /** Recovery 1: Pay on the Request screen with a top-up kept opens the sheet straight in Finishing Payment. */
    @Test
    fun `a Pay with a kept top-up from the Request screen opens the sheet in Finishing Payment`() {
        useCase("s2-min-top-up")
        rail.googlePay = CompletableDeferred()
        tapRequestScreenPay(flow())
        assertTrue(store.topUps.isNotEmpty(), "kept before Google Pay opened")
        scopes.forEach { it.cancel() }
        rail.delivery = CompletableDeferred()

        val relaunched = flow()
        tapRequestScreenPay(relaunched)
        assertFinishingInTheSheet(relaunched, "kept top-up")

        rail.delivery!!.complete(Unit)
        assertEquals(listOf(500L), rail.orders)
        assertEquals(1, paid)
    }

    /** Frame 1.3 to 3.3: Finishing Payment, the checkmark held in the same sheet, then the sheet closes. */
    @Test
    fun `the sheet holds the checkmark once the Request screen's payment lands, then closes`() {
        useCase("s1-no-balance")
        val f = flow()

        tapRequestScreenPay(f)

        assertEquals(1, paid)
        val held = f.state.value
        assertTrue(held.open && held.progress.success && !held.finishing, "the checkmark in the sheet, was $held")
        runBlocking { withTimeout(5_000) { f.state.first { !it.open } } }
        assertTrue(screens.isEmpty())
    }

    /**
     * Frame 3.3: the checkmark draws the plan it paid with. Section 1's payment spends the Dollars
     * it added, and a replan from what is left would put "You're $10.00 Short" under the checkmark.
     */
    @Test
    fun `1_3 to 3_3 - the checkmark draws the plan it paid with, not the balance the payment left`() {
        useCase("s1-no-balance")
        coEvery { payments.pay(any(), any(), any(), any()) } answers {
            dollarsMicros -= request.amount.quarks
            Result.success(Unit)
        }
        val f = flow()
        CoroutineScope(Dispatchers.Unconfined).also(scopes::add).launch { f.fundingUi.collect {} }

        tapRequestScreenPay(f)

        assertTrue(f.state.value.progress.success, "the checkmark, was ${f.state.value}")
        assertEquals(0L, dollarsMicros, "the payment spent the Dollars")
        assertNull(assertNotNull(f.fundingUi.value).short, "no short line under the checkmark")
        assertTrue(f.state.value.open, "in the sheet")
    }

    /**
     * The plan held for the checkmark ends with the sheet: the next collection's sheet draws its own
     * plan over the balances the payment left, not the one the last payment paid with.
     */
    @Test
    fun `the next collection's sheet draws its own plan, not the one the last payment held`() {
        useCase("s1-no-balance")
        every { payments.planCollection(any(), any()) } answers { planNow(firstArg<Fiat>().quarks) }
        coEvery { payments.pay(any(), any(), any(), any()) } answers {
            dollarsMicros -= firstArg<ShareToPay.Ready>().amount.quarks
            Result.success(Unit)
        }
        val next = ShareToPay.Ready(
            request = mockk(relaxed = true) {
                every { id } returns OTHER_REQUEST_ID
                every { collection } returns mockk(relaxed = true)
            },
            amount = Fiat(quarks = 4_000_000L, currencyCode = CurrencyCode.USD),
            intentId = mockk(relaxed = true),
        )
        coEvery { payments.prepare(OTHER_REQUEST_ID, any()) } returns Result.success(next)
        val f = flow()
        CoroutineScope(Dispatchers.Unconfined).also(scopes::add).launch { f.fundingUi.collect {} }

        tapRequestScreenPay(f)

        assertTrue(f.state.value.progress.success, "the checkmark, was ${f.state.value}")
        val held = assertNotNull(f.plan.value, "the plan under the checkmark")
        assertTrue(held.payableWithoutTopUp, "the checkmark draws the plan it paid with, was $held")
        f.start(OTHER_REQUEST_ID, fund = true)
        assertEquals(REQUEST_ID, f.state.value.requestId, "no second sheet during the hold")
        runBlocking { withTimeout(5_000) { f.state.first { !it.open } } }

        f.start(OTHER_REQUEST_ID, fund = true)

        assertTrue(f.state.value.open, "the next collection's sheet, was ${f.state.value}")
        assertEquals(OTHER_REQUEST_ID, f.state.value.requestId)
        assertEquals(0, nothingToPay)
        val live = planNow(next.amount.quarks)
        assertEquals(false, live.payableWithoutTopUp, "the Dollars were spent, so the next one is short")
        // The hold's delay releases on another thread, so read the plan once it settles. A held
        // plan that outlived the sheet never settles to [live] and is what the assert reports.
        val drawn = runBlocking { withTimeoutOrNull(5_000) { f.plan.first { it == live } } } ?: f.plan.value
        assertEquals(live, drawn, "the next collection's plan over the balances left")
        assertNotEquals(held, drawn)
        val ui = runBlocking { withTimeoutOrNull(5_000) { f.fundingUi.first { it?.short != null } } } ?: f.fundingUi.value
        assertNotNull(assertNotNull(ui).short, "its short line")
    }

    /** Pay step 4: the 60 s deadline unlocks the sheet on "Payment Not Confirmed"; it doesn't close. */
    @Test
    fun `a payment not confirmed after the Request screen's top-up leaves the sheet up and unlocked`() {
        every { resources.getString(any<Int>()) } answers { "string ${firstArg<Int>()}" }
        useCase("s1-no-balance")
        coEvery { payments.pay(any(), any(), any(), any()) } returns Result.failure(PaymentNotConfirmed())
        val f = flow()

        tapRequestScreenPay(f)

        val state = f.state.value
        assertTrue(state.open && !state.finishing, "the sheet stays up, out of Finishing Payment, was $state")
        assertTrue(state.progress.isIdle, "the sheet can be left")
        assertEquals("string ${R.string.error_title_paymentNotConfirmed}", BottomBarManager.messages.value.single().title)
    }

    /** A failed payment after the Request screen's top-up stays in the sheet, and its retry pays from the Dollars. */
    @Test
    fun `a payment failing after the Request screen's top-up leaves the sheet up, and the retry charges nothing`() {
        useCase("s1-no-balance")
        payFailures = 1
        val f = flow()

        tapRequestScreenPay(f)

        val state = f.state.value
        assertTrue(state.open && !state.finishing && state.progress.isIdle, "the sheet, unlocked, was $state")
        assertEquals(false, state.direct, "a later stop from this sheet leaves it up")

        f.confirm()

        assertEquals(listOf(1000L), rail.orders)
        assertEquals(1, paid)
    }

    /** No short state for sections 1 and 2: a top-up that won't land closes the sheet back to the Request screen's button. */
    @Test
    fun `a top-up still pending after the Request screen's Pay closes the sheet back to the button`() {
        every { resources.getString(any<Int>()) } answers { "string ${firstArg<Int>()}" }
        useCase("s1-no-balance")
        rail.delivery = CompletableDeferred()
        rail.deliveryResult = TopUpDelivery.TimedOut
        val f = flow()
        tapRequestScreenPay(f)
        assertFinishingInTheSheet(f, "section 1")

        rail.delivery!!.complete(Unit)

        val state = f.state.value
        assertTrue(!state.open && !state.finishing && state.progress.isIdle, "back to the button, was $state")
        assertEquals("string ${R.string.error_title_collectionTopUpPending}", BottomBarManager.messages.value.single().title)
        assertEquals(0, paid)
    }

    /** Recovery 1 from the Request screen: the relaunch's tap, or the deck's Pay, waits in the sheet and pays once. */
    @Test
    fun `process death after the Request screen's top-up - the next Pay waits on it in the sheet and orders nothing`() {
        for (reopen in listOf("request screen", "deck")) {
            setUp()
            rail.orders.clear()
            store.topUps.clear()
            paid = 0
            useCase("s2-min-top-up")
            rail.googlePay = CompletableDeferred()
            tapRequestScreenPay(flow())
            assertTrue(store.topUps.isNotEmpty(), "$reopen: kept before Google Pay opened")
            scopes.forEach { it.cancel() }

            val relaunched = flow()
            if (reopen == "deck") openSheet(relaunched) else tapRequestScreenPay(relaunched)

            assertEquals(listOf(500L), rail.orders, reopen)
            assertEquals(1, paid, reopen)
            assertTrue(store.topUps.isEmpty(), reopen)
            assertTrue(relaunched.state.value.open && relaunched.state.value.progress.success, "$reopen: the checkmark in the sheet")
            assertTrue(screens.isEmpty(), reopen)
        }
    }

    /** Recovery 4 from the Request screen: still short after the Dollars land, the sheet asks. */
    @Test
    fun `a short delivery from the Request screen opens the sheet's short state`() {
        useCase("s1-no-balance")
        rail.deliverMicros = 4_000_000L
        val f = flow()

        tapRequestScreenPay(f)

        assertEquals(listOf(1000L), rail.orders)
        assertEquals(0, paid)
        assertTrue(f.state.value.open)
        assertEquals(false, f.state.value.direct)
        assertTrue(f.state.value.progress.isIdle)
        assertEquals(1, rail.orders.size, "nothing more is ordered without a tap")
    }

    @Test
    fun `closing Google Pay over the Request screen keeps nothing, and the next tap orders again`() {
        useCase("s1-no-balance")
        rail.payment = TopUpPayment.NotCharged
        val f = flow()

        tapRequestScreenPay(f)
        assertTrue(store.topUps.isEmpty())
        assertTrue(f.state.value.progress.isIdle)
        assertEquals(false, f.state.value.open)
        assertEquals(0, paid)

        rail.payment = TopUpPayment.Succeeded
        tapRequestScreenPay(f)

        assertEquals(listOf(1000L, 1000L), rail.orders)
        assertEquals(1, paid)
    }

    @Test
    fun `the send limit is checked before the Request screen's top-up orders`() {
        useCase("s1-no-balance")
        val limit = mockk<SendLimit>(relaxed = true) { every { nextTransaction } returns 1.0 }
        every { transactionController.limits } returns MutableStateFlow<Limits?>(
            mockk<Limits>(relaxed = true) { every { sendLimitFor(any()) } returns limit },
        )
        val f = flow()

        tapRequestScreenPay(f)

        assertTrue(rail.orders.isEmpty())
        assertTrue(f.state.value.progress.isIdle)
    }

    /** Group Money off: a split's Pay is the shipped sheet, whatever the button asks for. */
    @Test
    fun `without funding, the Request screen's Pay opens the shipped sheet`() {
        useCase("s1-no-balance")
        coEvery { payer.mayProceed(any(), any(), any()) } returns true
        val f = flow()

        f.start(REQUEST_ID, fund = false, direct = true)

        assertTrue(f.state.value.open)
        assertEquals(false, f.state.value.funding)
        assertEquals(false, f.state.value.direct)
        assertTrue(rail.orders.isEmpty())
        assertEquals(null, runBlocking { f.directPay(flowOf(null)).first() }, "no amount with the flag off")
    }

    /** The wallet's tokens. A relaunch starts with Dollars alone, and the first update loads the rest. */
    private val wallet = MutableStateFlow(listOf(Token.usdf, MOONY))

    /** Whether the wallet still holds Moony: false once it can no longer be paid in. */
    private var holdsMoony = true

    /**
     * The spec's S5 case for a top-up's currency: $6.00 paid in Moony from $1.50 Moony and $2.50
     * Dollars is $2.00 short, so it tops up the $5.00 minimum. Planned in whichever token the flow
     * asks for, so the plan follows the currency it pays in.
     */
    private fun useMoonyCase() {
        dollarsMicros = 2_500_000L
        request = ShareToPay.Ready(
            request = mockk(relaxed = true) {
                every { id } returns REQUEST_ID
                every { collection } returns mockk(relaxed = true)
            },
            amount = Fiat(quarks = 6_000_000L, currencyCode = CurrencyCode.USD),
            intentId = mockk(relaxed = true),
        )
        every { tokenCoordinator.tokens } returns wallet
        coEvery { tokenCoordinator.update() } answers {
            wallet.value = if (holdsMoony) listOf(Token.usdf, MOONY) else listOf(Token.usdf)
        }
        every { payments.planCollection(any(), any()) } answers { planIn(secondArg()) }
    }

    private fun planIn(token: Token) = planCollectionFunding(
        amountMicros = 6_000_000L,
        minorDigits = 2,
        minTopUpMicros = 5_000_000L,
        dollarsId = DOLLARS,
        selected = if (token.address == Mint.usdf) DOLLARS else MOONY_ID,
        lastPaid = null,
        balances = listOf(FundingBalance(MOONY_ID, 1_500_000L, 0), FundingBalance(DOLLARS, dollarsMicros, 0)),
    )

    /**
     * Picks Moony in the sheet and taps Add and Pay, then the process dies after Google Pay charged
     * the $5.00 order, on "Finishing Payment" before the Dollars land. The relaunch reads tokens
     * before Moony's is loaded again.
     */
    private fun topUpInMoonyThenDie() {
        useMoonyCase()
        rail.delivery = CompletableDeferred()
        val f = openSheet()
        f.pickCollectionCurrency(MOONY.address)
        assertEquals(MOONY, f.token.value)

        f.onAddAndPay()

        assertEquals(listOf(500L), rail.orders)
        assertTrue(f.state.value.finishing, "Google Pay closed on a charge")
        assertTrue(store.topUps.isNotEmpty(), "kept until the Dollars land")
        scopes.forEach { it.cancel() }
        rail.delivery = null
        wallet.value = listOf(Token.usdf)
        assertEquals(0, paid)
    }

    private fun assertPaidOnceIn(token: Token) {
        assertEquals(1, paid)
        coVerify(exactly = 1) { payments.pay(any(), any(), any(), any()) }
        coVerify(exactly = 1) { payments.pay(any(), token, owner, true) }
    }

    /** Recovery 1 (spec PR #134): the relaunch replans in the currency picked before Google Pay. */
    @Test
    fun `process death between Google Pay and the landing pays in the currency picked before Google Pay`() {
        topUpInMoonyThenDie()

        val relaunched = openSheet()

        assertEquals(listOf(500L), rail.orders)
        assertTrue(store.topUps.isEmpty())
        assertEquals(7_500_000L, dollarsMicros)
        coVerify { payments.planCollection(any(), MOONY) }
        assertPaidOnceIn(MOONY)
        assertEquals(MOONY, relaunched.token.value)
    }

    @Test
    fun `the Request screen's Pay after a relaunch pays in the currency picked before Google Pay`() {
        topUpInMoonyThenDie()

        tapRequestScreenPay(flow())

        assertEquals(listOf(500L), rail.orders)
        assertPaidOnceIn(MOONY)
    }

    @Test
    fun `a top-up that timed out pays in its currency when the sheet is closed and opened again`() {
        useMoonyCase()
        rail.deliveryResult = TopUpDelivery.TimedOut
        val f = openSheet()
        f.pickCollectionCurrency(MOONY.address)
        f.onAddAndPay()
        assertEquals(0, paid)
        assertTrue(store.topUps.isNotEmpty())

        f.dismiss()
        rail.deliveryResult = TopUpDelivery.Delivered
        openSheet(f)

        assertEquals(listOf(500L), rail.orders)
        assertPaidOnceIn(MOONY)
    }

    @Test
    fun `after a relaunch the open sheet keeps the top-up's currency, so a tap once it lands pays in it`() {
        topUpInMoonyThenDie()
        rail.deliveryResult = TopUpDelivery.TimedOut
        val relaunched = openSheet()
        assertEquals(0, paid)
        assertTrue(relaunched.state.value.progress.isIdle)
        assertEquals(MOONY, relaunched.token.value)

        rail.deliveryResult = TopUpDelivery.Delivered
        relaunched.onAddAndPay()

        assertEquals(listOf(500L), rail.orders)
        assertPaidOnceIn(MOONY)
    }

    /** Spec PR #134: a currency that can no longer be paid in shows the sheet, never a silent switch. */
    @Test
    fun `a top-up whose currency can no longer be paid in pays nothing and opens the sheet`() {
        topUpInMoonyThenDie()
        holdsMoony = false

        val relaunched = flow()
        tapRequestScreenPay(relaunched)

        assertEquals(listOf(500L), rail.orders)
        assertEquals(0, paid)
        coVerify(exactly = 0) { payments.pay(any(), any(), any(), any()) }
        assertTrue(store.topUps.isEmpty(), "the Dollars landed and stay")
        assertEquals(7_500_000L, dollarsMicros)
        assertTrue(relaunched.state.value.open)
        assertEquals(false, relaunched.state.value.direct)
        assertTrue(relaunched.state.value.progress.isIdle)
        assertEquals(false, relaunched.state.value.finishing)
        assertEquals(Token.usdf, relaunched.token.value, "the sheet shows the currency it would now pay in")
    }

    @Test
    fun `a top-up an older build kept, with no currency, pays in the default currency`() {
        topUpInMoonyThenDie()
        store.topUps.replaceAll { _, record -> record.copy(payingCurrency = null) }

        val relaunched = openSheet()

        assertEquals(listOf(500L), rail.orders)
        assertPaidOnceIn(Token.usdf)
        assertEquals(Token.usdf, relaunched.token.value)
    }

    @Test
    fun `a short landing after a relaunch shows the short state in the top-up's currency`() {
        topUpInMoonyThenDie()
        rail.deliverMicros = 1_000_000L

        val relaunched = openSheet()

        assertEquals(0, paid)
        assertEquals(listOf(500L), rail.orders)
        assertTrue(relaunched.state.value.open)
        assertTrue(relaunched.state.value.progress.isIdle)
        assertEquals(false, planIn(MOONY).payableWithoutTopUp)
        assertEquals(MOONY, relaunched.token.value)
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map(::getJSONObject)

    private companion object {
        const val FIXTURE = "../../shared/tokens/src/androidHostTest/resources/collection_funding.json"
        const val DOLLARS = "dollars"
        val REQUEST_ID = listOf<Byte>(2)
        val OTHER_REQUEST_ID = listOf<Byte>(3)
        const val MICROS_PER_CENT = 10_000L
        const val MOONY_ID = "moony"
        val MOONY = Token.usdf.copy(address = Mint("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"))
        val SECTIONS = listOf("s1-no-balance", "s2-min-top-up", "s4-add-then-pay", "s7-short-under-min", "s8-short-over-min")
    }
}
