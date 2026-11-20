package com.flipcash.shared.payments.request

import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.app.tokens.FundingLeg
import com.getcode.opencode.controllers.TransactionOperations
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.exchange.VerifiedFiat
import com.getcode.opencode.exchange.VerifiedFiatCalculator
import com.getcode.opencode.internal.manager.VerifiedState
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.core.errors.GetPendingSwapsError
import com.getcode.opencode.model.core.errors.GetSwapError
import com.getcode.opencode.model.core.errors.SwapError
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.usdf
import com.getcode.opencode.model.transactions.Swap
import com.getcode.opencode.model.transactions.SwapFundingSource
import com.getcode.opencode.model.transactions.SwapMetadata
import com.getcode.opencode.model.transactions.SwapState
import com.getcode.opencode.model.transactions.VerifiedSwapMetadata
import com.getcode.opencode.repositories.SwapRepository
import com.getcode.opencode.utils.generate
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.registerInstanceFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration

class CollectionFunderTest {

    private val requestId: ID = List(32) { 4 }
    private val owner = mockk<AccountCluster>(relaxed = true)
    private val state = mockk<VerifiedState>()

    private val jeffy = token(11)
    private val bob = token(12)
    private val al = token(13)
    private val tokens = listOf(Token.usdf, jeffy, bob, al).associateBy { it.address }

    /** Balances in USD micros, as the wallet holds them; a leg's result lands on [pending]. */
    private val balances = mutableMapOf<Mint, Long>()
    private val pending = mutableMapOf<Mint, Long>()

    /** What a finalised leg delivers, as a share of what the plan said it would. */
    private var delivery: (from: Mint, round: Int) -> Double = { _, _ -> 1.0 }
    private var round = 0

    /** The rate of the request's currency this round; the plan's micros are in that currency. */
    private var fx = 1.0

    private val tokenCoordinator = mockk<TokenCoordinator>(relaxed = true)
    private val transactions = mockk<TransactionOperations>()
    private val swaps = mockk<SwapRepository>()
    private val calculator = mockk<VerifiedFiatCalculator>()
    private val exchange = mockk<Exchange>()
    private val store = InMemoryCollectionFundingStore()
    private val planner = CollectionFundingPlanner(tokenCoordinator, exchange)

    private val funder = CollectionFunder(
        transactions, swaps, tokenCoordinator, calculator, exchange, planner, store,
    ).also { it.settleInterval = Duration.ZERO }

    /** Every leg submitted, in order: from, to, swap id, funding intent id, priced amount. */
    private val submitted = mutableListOf<Submitted>()
    private val computed = mutableListOf<Triple<Fiat, Token, Rate>>()

    private data class Submitted(val from: Mint, val to: Mint, val swapId: SwapId, val fundingId: List<Byte>, val amount: VerifiedFiat)

    /** The leg each source is running under the plan of its round, for the fake's delivery. */
    private val legs = mutableMapOf<Mint, FundingLeg>()

    private val usd = Rate(1.0, CurrencyCode.USD)
    private val eur = Rate(0.5, CurrencyCode.EUR)

    init {
        // A matcher needs a placeholder swap id, and a random one isn't 32 bytes.
        registerInstanceFactory { SwapId.generate() }
        every { exchange.rateFor(CurrencyCode.USD) } returns usd
        every { exchange.rateFor(CurrencyCode.EUR) } returns eur
        every { tokenCoordinator.cachedTokens() } answers { tokens.values.toList() }
        every { tokenCoordinator.cachedToken(any()) } answers { tokens[firstArg()] }
        every { tokenCoordinator.currentBalance(any()) } answers { Fiat(balances[firstArg()] ?: 0L, CurrencyCode.USD) }
        every { tokenCoordinator.balanceForToken(any<Token>()) } answers {
            Fiat(balances[firstArg<Token>().address] ?: 0L, CurrencyCode.USD)
        }
        every { tokenCoordinator.heldQuarksForToken(any()) } answers { flowOf(balances[firstArg()]) }
        coEvery { tokenCoordinator.update() } answers {
            pending.forEach { (mint, micros) -> balances[mint] = (balances[mint] ?: 0L) + micros }
            pending.clear()
        }
        coEvery { calculator.compute(any(), any(), any(), any(), any()) } answers {
            val amount = firstArg<Fiat>()
            val token = secondArg<Token>()
            computed += Triple(amount, token, arg(3))
            Result.success(
                VerifiedFiat(LocalFiat(Fiat(amount.quarks, CurrencyCode.USD), amount, arg(3), token.address), state),
            )
        }
        coEvery { transactions.sell(owner, any(), any(), any(), any()) } answers {
            submit(arg<Token>(2).address, Mint.usdf, arg(3), arg(4), arg(1))
        }
        coEvery { transactions.buy(owner, any(), any(), any(), any(), any(), any()) } answers {
            submit(Mint.usdf, arg<Token>(4).address, arg(3), arg(5), arg(1))
        }
        coEvery { transactions.swap(owner, any(), any(), any(), any(), any(), any(), any()) } answers {
            submit(arg<Token>(3).address, arg<Token>(4).address, arg(5), arg(6), arg(1))
        }
        coEvery { transactions.pollSwapForState(any(), owner, SwapState.FINALIZED, any(), any()) } answers {
            land(submitted.last { it.swapId == swapIdOf(firstArg()) })
            Result.success(mockk())
        }
    }

    private fun token(seed: Int) = mockk<Token>(relaxed = true) {
        every { this@mockk.address } returns Mint(List(32) { seed.toByte() })
        every { launchpadMetadata } returns null
    }

    private fun submit(from: Mint, to: Mint, swapId: Any?, source: SwapFundingSource?, amount: VerifiedFiat): Result<SwapId> {
        val id = requireNotNull(swapIdOf(swapId)) { "funding must pass the swap id it kept" }
        val funding = assertIs<SwapFundingSource.SubmitIntent>(source).id
        submitted += Submitted(from, to, id, funding, amount)
        return Result.success(id)
    }

    /** mockk passes a value-class argument unboxed, as its byte list. */
    @Suppress("UNCHECKED_CAST")
    private fun swapIdOf(raw: Any?): SwapId? = when (raw) {
        null -> null
        is SwapId -> raw
        else -> SwapId(raw as List<Byte>)
    }

    /** Settles [leg]: its source pays what it sold now, its delivery reaches the next fetch. */
    private fun land(leg: Submitted) {
        val planned = requireNotNull(legs[leg.from]) { "no planned leg from ${leg.from}" }
        balances[leg.from] = (balances[leg.from] ?: 0L) - (planned.soldMicros / fx).roundToLong()
        val delivered = (planned.deliveredMicros * delivery(leg.from, round) / fx).roundToLong()
        pending[leg.to] = (pending[leg.to] ?: 0L) + delivered
    }

    private fun hold(vararg held: Pair<Token, String>) {
        held.forEach { (token, amount) -> balances[token.address] = Fiat(amount, CurrencyCode.USD).quarks }
    }

    /** Runs funding, recording each round's planned legs so the fake can deliver them. */
    private suspend fun fund(amount: Fiat, paying: Token): Result<Unit> {
        every { exchange.rateFor(amount.currencyCode) } answers {
            // Each planning pass reads the rate first: note this round's legs from the same balances.
            val rate = if (amount.currencyCode == CurrencyCode.EUR) eur else usd
            round++
            fx = rate.fx
            planner.plan(amount, paying.address, planner.balances(rate), rate).legs
                .filter { it.conversion }
                .forEach { legs[Mint(it.source)] = it }
            rate
        }
        return funder.fund(requestId, amount, paying, owner)
    }

    @Test
    fun `legs run in the plan's order, each waited on, before funding returns`() = runTest {
        hold(Token.usdf to "3.00", bob to "4.00", al to "20.00")

        assertTrue(fund(Fiat("10.00", CurrencyCode.USD), jeffy).isSuccess)

        // Dollars first, then the others smallest first; every leg converts into the paying currency.
        assertEquals(listOf(Mint.usdf, bob.address, al.address), submitted.map { it.from })
        assertTrue(submitted.all { it.to == jeffy.address })
        coVerify(exactly = 3) { transactions.pollSwapForState(any(), owner, SwapState.FINALIZED, any(), any()) }
        coVerify(exactly = 1) { transactions.buy(owner, any(), null, any(), jeffy, any(), any()) }
        coVerify(exactly = 2) { transactions.swap(owner, any(), null, any(), jeffy, any(), any(), any()) }
        assertTrue((balances[jeffy.address] ?: 0L) >= Fiat("10.00", CurrencyCode.USD).quarks)
        assertTrue(store.legs(requestId).isEmpty())
    }

    @Test
    fun `into Dollars a leg is a sell`() = runTest {
        hold(Token.usdf to "2.00", al to "20.00")

        assertTrue(fund(Fiat("10.00", CurrencyCode.USD), Token.usdf).isSuccess)

        coVerify(exactly = 1) { transactions.sell(owner, any(), al, any(), any()) }
        coVerify(exactly = 0) { transactions.buy(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a balance that already covers the payment converts nothing`() = runTest {
        hold(Token.usdf to "12.00", al to "20.00")

        assertTrue(fund(Fiat("10.00", CurrencyCode.USD), Token.usdf).isSuccess)

        assertTrue(submitted.isEmpty())
    }

    @Test
    fun `a short plan runs no leg and says how short`() = runTest {
        hold(Token.usdf to "2.00", al to "3.00")

        val error = assertIs<CollectionFundingError.Short>(fund(Fiat("10.00", CurrencyCode.USD), Token.usdf).exceptionOrNull())

        assertTrue(error.plan.shortfallMicros > 0)
        assertTrue(submitted.isEmpty())
    }

    @Test
    fun `a leg is priced in the request's currency at its rate, a buy in Dollars`() = runTest {
        hold(Token.usdf to "30.00")

        assertTrue(fund(Fiat("10.00", CurrencyCode.EUR), jeffy).isSuccess)

        val (amount, token, rate) = computed.single()
        assertEquals(CurrencyCode.EUR, amount.currencyCode)
        assertEquals(Fiat("10.00", CurrencyCode.EUR), amount)
        assertEquals(Token.usdf, token)
        assertEquals(eur, rate)
    }

    @Test
    fun `a drained leg sells every quark held`() = runTest {
        hold(Token.usdf to "2.00", bob to "4.00", al to "20.00")
        balances[bob.address] = 4_000_017L

        assertTrue(fund(Fiat("10.00", CurrencyCode.USD), Token.usdf).isSuccess)

        val drained = submitted.first { it.from == bob.address }
        assertEquals(4_000_017L, drained.amount.localFiat.underlyingTokenAmount.quarks)
    }

    @Test
    fun `a kept swap the server never saw starts again under both kept ids`() = runTest {
        hold(Token.usdf to "2.00", al to "20.00")
        val offline = IllegalStateException("offline")
        coEvery { transactions.sell(owner, any(), al, any(), any()) } answers {
            submit(al.address, Mint.usdf, arg(3), arg(4), arg(1))
            Result.failure(offline)
        }

        assertEquals(offline, fund(Fiat("10.00", CurrencyCode.USD), Token.usdf).exceptionOrNull())
        val kept = store.legs(requestId).single()

        // The server never saw it, so the retry submits the same swap again.
        coEvery { swaps.getSwap(kept.swapId, any()) } returns Result.failure(GetSwapError.NotFound())
        coEvery { transactions.sell(owner, any(), al, any(), any()) } answers {
            submit(al.address, Mint.usdf, arg(3), arg(4), arg(1))
        }
        assertTrue(fund(Fiat("10.00", CurrencyCode.USD), Token.usdf).isSuccess)

        assertEquals(2, submitted.size)
        assertEquals(submitted[0].swapId, submitted[1].swapId)
        assertEquals(submitted[0].fundingId, submitted[1].fundingId)
        assertEquals(kept.swapId, submitted[1].swapId)
        assertEquals(kept.fundingIntentId, submitted[1].fundingId)
        coVerify(exactly = 0) { swaps.getPendingSwaps(any()) }
        coVerify(exactly = 0) { transactions.fundSwap(any(), any(), any(), any()) }
        assertTrue(store.legs(requestId).isEmpty())
    }

    @Test
    fun `a retry waits on a kept swap the server has, and doesn't convert again`() = runTest {
        hold(Token.usdf to "2.00", al to "20.00")
        coEvery { transactions.pollSwapForState(any(), owner, SwapState.FINALIZED, any(), any()) } returns
            Result.failure(IllegalStateException("timed out"))

        assertTrue(fund(Fiat("10.00", CurrencyCode.USD), Token.usdf).isFailure)
        val kept = store.legs(requestId).single()

        coEvery { swaps.getSwap(kept.swapId, any()) } returns Result.success(mockk<Swap> { every { this@mockk.state } returns SwapState.FUNDING })
        coEvery { transactions.pollSwapForState(kept.swapId, owner, SwapState.FINALIZED, any(), any()) } answers {
            land(submitted.single())
            Result.success(mockk())
        }
        assertTrue(fund(Fiat("10.00", CurrencyCode.USD), Token.usdf).isSuccess)

        assertEquals(1, submitted.size)
        coVerify(exactly = 1) { transactions.sell(any(), any(), any(), any(), any()) }
        assertTrue(store.legs(requestId).isEmpty())
    }

    /** A first attempt whose leg was submitted but never settled, leaving its record kept. */
    private suspend fun keepUnsettledLeg(): FundingLegRecord {
        hold(Token.usdf to "2.00", al to "20.00")
        coEvery { transactions.pollSwapForState(any(), owner, SwapState.FINALIZED, any(), any()) } returns
            Result.failure(SwapError.Timeout())
        assertTrue(fund(Fiat("10.00", CurrencyCode.USD), Token.usdf).isFailure)
        return store.legs(requestId).single()
    }

    private fun serverSays(record: FundingLegRecord, state: SwapState) {
        coEvery { swaps.getSwap(record.swapId, any()) } returns
            Result.success(mockk<Swap> { every { this@mockk.state } returns state })
    }

    private fun legsSettle() {
        coEvery { transactions.pollSwapForState(any(), owner, SwapState.FINALIZED, any(), any()) } answers {
            land(submitted.last { it.swapId == swapIdOf(firstArg()) })
            Result.success(mockk())
        }
    }

    /** Every funding intent submitted for a swap the server already had: from, funding id, priced amount. */
    private data class Funded(val from: Token, val fundingId: List<Byte>, val amount: VerifiedFiat)

    private val funded = mutableListOf<Funded>()
    private val pricedQuarks = mutableListOf<Long>()

    /**
     * The server lists [record]'s swap as pending in [state], recorded under [fundingId] with the
     * given amounts in quarks of the leg's source; pricing those quarks and funding succeed.
     */
    private fun serverHasPending(
        record: FundingLegRecord,
        fundingId: List<Byte>,
        state: SwapState = SwapState.CREATED,
        swapQuarks: Long = 8_000_000L,
        feeQuarks: Long = 40_000L,
    ) {
        val swap = SwapMetadata(
            verifiedMetadata = VerifiedSwapMetadata.Reserve(
                id = record.swapId,
                fromMint = record.from,
                toMint = record.to,
                swapAmount = Fiat(swapQuarks, CurrencyCode.USD),
                feeAmount = Fiat(feeQuarks, CurrencyCode.USD),
                fundingSource = SwapFundingSource.SubmitIntent(fundingId),
            ),
            state = state,
            signature = emptyList(),
        )
        coEvery { swaps.getPendingSwaps(any()) } returns Result.success(listOf(swap))
        coEvery { calculator.computeForQuarks(any(), any(), any()) } answers {
            val quarks = firstArg<Long>()
            pricedQuarks += quarks
            val usd = Fiat(quarks, CurrencyCode.USD)
            Result.success(VerifiedFiat(LocalFiat(usd, usd, arg(2), secondArg<Token>().address), this@CollectionFunderTest.state))
        }
        coEvery { transactions.fundSwap(owner, any(), any(), any()) } answers {
            funded += Funded(arg(1), arg(3), arg(2))
            Result.success(Unit)
        }
    }

    @Test
    fun `a kept swap the server reports CREATED is funded once under its recorded id, not started again`() = runTest {
        val kept = keepUnsettledLeg()
        serverSays(kept, SwapState.CREATED)
        // The server's funding id differs from the kept one: the server's wins.
        val recordedId = PublicKey.generate().bytes
        assertNotEquals(kept.fundingIntentId, recordedId)
        serverHasPending(kept, recordedId, swapQuarks = 8_000_000L, feeQuarks = 40_000L)
        legsSettle()

        assertTrue(fund(Fiat("10.00", CurrencyCode.USD), Token.usdf).isSuccess)

        val funding = funded.single()
        assertEquals(recordedId, funding.fundingId)
        assertEquals(al, funding.from)
        // It moves what the server recorded: the swap's amount plus its fee.
        assertEquals(listOf(8_040_000L), pricedQuarks)
        assertEquals(8_040_000L, funding.amount.localFiat.underlyingTokenAmount.quarks)
        // Only the first attempt started the swap; the retry never starts it again.
        assertEquals(1, submitted.size)
        coVerify(exactly = 1) { transactions.sell(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { transactions.buy(any(), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { transactions.swap(any(), any(), any(), any(), any(), any(), any(), any()) }
        assertTrue(store.legs(requestId).isEmpty())
    }

    @Test
    fun `a CREATED swap funded on retry that still hasn't settled stops on the pending state and keeps its ids`() = runTest {
        val kept = keepUnsettledLeg()
        serverSays(kept, SwapState.CREATED)
        serverHasPending(kept, kept.fundingIntentId)

        val result = fund(Fiat("10.00", CurrencyCode.USD), Token.usdf)

        assertIs<CollectionFundingError.ConversionPending>(result.exceptionOrNull())
        assertEquals(1, funded.size)
        assertEquals(1, submitted.size)
        assertEquals(kept, store.legs(requestId).single())
    }

    @Test
    fun `a CREATED swap missing from the pending swaps is waited on, not funded or started again`() = runTest {
        val kept = keepUnsettledLeg()
        serverSays(kept, SwapState.CREATED)
        // The server reports an empty pending list as not found.
        coEvery { swaps.getPendingSwaps(any()) } returns Result.failure(GetPendingSwapsError.NotFound())
        legsSettle()

        assertTrue(fund(Fiat("10.00", CurrencyCode.USD), Token.usdf).isSuccess)

        assertEquals(1, submitted.size)
        coVerify(exactly = 0) { transactions.fundSwap(any(), any(), any(), any()) }
        coVerify(exactly = 2) { transactions.pollSwapForState(kept.swapId, owner, SwapState.FINALIZED, any(), any()) }
        assertTrue(store.legs(requestId).isEmpty())
    }

    @Test
    fun `a CREATED swap the pending list shows as FUNDED is waited on, not funded again`() = runTest {
        val kept = keepUnsettledLeg()
        serverSays(kept, SwapState.CREATED)
        serverHasPending(kept, kept.fundingIntentId, state = SwapState.FUNDED)
        legsSettle()

        assertTrue(fund(Fiat("10.00", CurrencyCode.USD), Token.usdf).isSuccess)

        assertTrue(funded.isEmpty())
        assertEquals(1, submitted.size)
        assertTrue(store.legs(requestId).isEmpty())
    }

    @Test
    fun `a kept swap the server reports FAILED gets a fresh id`() = runTest {
        val kept = keepUnsettledLeg()
        serverSays(kept, SwapState.FAILED)
        legsSettle()

        assertTrue(fund(Fiat("10.00", CurrencyCode.USD), Token.usdf).isSuccess)

        assertEquals(2, submitted.size)
        assertNotEquals(kept.swapId, submitted[1].swapId)
        assertNotEquals(kept.fundingIntentId, submitted[1].fundingId)
        // Only the first attempt waited on the failed swap; the retry doesn't.
        coVerify(exactly = 1) { transactions.pollSwapForState(kept.swapId, owner, any(), any(), any()) }
        assertTrue(store.legs(requestId).isEmpty())
    }

    @Test
    fun `a kept swap the server reports CANCELLED is treated as failed and gets a fresh id`() = runTest {
        val kept = keepUnsettledLeg()
        serverSays(kept, SwapState.CANCELLED)
        legsSettle()

        assertTrue(fund(Fiat("10.00", CurrencyCode.USD), Token.usdf).isSuccess)

        assertEquals(2, submitted.size)
        assertNotEquals(kept.swapId, submitted[1].swapId)
        assertNotEquals(kept.fundingIntentId, submitted[1].fundingId)
        assertTrue(store.legs(requestId).isEmpty())
    }

    @Test
    fun `a kept swap that is CANCELLING is waited on, and mints a fresh id only once cancelled`() = runTest {
        val kept = keepUnsettledLeg()
        serverSays(kept, SwapState.CANCELLING)
        coEvery { transactions.pollSwapForState(kept.swapId, owner, SwapState.CANCELLED, any(), any()) } returns
            Result.failure(SwapError.Timeout())

        // Still cancelling: nothing new is submitted, and the ids stay kept.
        assertIs<CollectionFundingError.ConversionPending>(fund(Fiat("10.00", CurrencyCode.USD), Token.usdf).exceptionOrNull())
        assertEquals(1, submitted.size)
        assertEquals(kept, store.legs(requestId).single())

        // Once the server cancels it, the retry converts under new ids.
        coEvery { transactions.pollSwapForState(kept.swapId, owner, SwapState.CANCELLED, any(), any()) } returns
            Result.success(mockk())
        legsSettle()
        assertTrue(fund(Fiat("10.00", CurrencyCode.USD), Token.usdf).isSuccess)

        assertEquals(2, submitted.size)
        assertNotEquals(kept.swapId, submitted[1].swapId)
        assertTrue(store.legs(requestId).isEmpty())
    }

    @Test
    fun `a kept swap still in flight after waiting stops on the pending state and converts nothing`() = runTest {
        val kept = keepUnsettledLeg()
        serverSays(kept, SwapState.FUNDING)

        val result = fund(Fiat("10.00", CurrencyCode.USD), Token.usdf)

        assertIs<CollectionFundingError.ConversionPending>(result.exceptionOrNull())
        assertEquals(1, submitted.size)
        assertEquals(kept, store.legs(requestId).single())
    }

    @Test
    fun `a leg the server fails while waiting drops its ids, so the retry mints anew`() = runTest {
        hold(Token.usdf to "2.00", al to "20.00")
        coEvery { transactions.pollSwapForState(any(), owner, SwapState.FINALIZED, any(), any()) } returns
            Result.failure(SwapError.Terminal(SwapState.FAILED))

        assertIs<SwapError.Terminal>(fund(Fiat("10.00", CurrencyCode.USD), Token.usdf).exceptionOrNull())

        assertTrue(store.legs(requestId).isEmpty())
    }

    @Test
    fun `a retry drops a kept swap that already finalised`() = runTest {
        hold(Token.usdf to "12.00")
        val finalised = FundingLegRecord(al.address, Mint.usdf, SwapId.generate(), List(32) { 3 })
        store.put(requestId, finalised)
        coEvery { swaps.getSwap(finalised.swapId, any()) } returns
            Result.success(mockk<Swap> { every { this@mockk.state } returns SwapState.FINALIZED })

        assertTrue(fund(Fiat("10.00", CurrencyCode.USD), Token.usdf).isSuccess)

        assertTrue(submitted.isEmpty())
        coVerify(exactly = 0) { transactions.pollSwapForState(any(), any(), any(), any(), any()) }
        assertTrue(store.legs(requestId).isEmpty())
    }

    @Test
    fun `a leg that lands short replans from the fetched balances and converts more when they cover it`() = runTest {
        hold(Token.usdf to "2.00", bob to "5.00", al to "30.00")
        // Al delivers half its planned amount the first round.
        delivery = { from, round -> if (from == al.address && round == 1) 0.5 else 1.0 }

        assertTrue(fund(Fiat("10.00", CurrencyCode.USD), Token.usdf).isSuccess)

        assertEquals(listOf(bob.address, al.address, al.address), submitted.map { it.from })
        // The second Al leg is a new conversion, under its own swap id.
        assertNotEquals(submitted[1].swapId, submitted[2].swapId)
        assertTrue((balances[Mint.usdf] ?: 0L) >= Fiat("10.00", CurrencyCode.USD).quarks)
    }

    @Test
    fun `a leg that lands short with nothing left to convert stops short`() = runTest {
        hold(Token.usdf to "2.00", al to "8.20")
        delivery = { _, _ -> 0.5 }

        assertIs<CollectionFundingError.Short>(fund(Fiat("10.00", CurrencyCode.USD), Token.usdf).exceptionOrNull())

        assertEquals(1, submitted.size)
    }

    @Test
    fun `a finalised leg whose result never reaches the balance converts nothing more`() = runTest {
        hold(Token.usdf to "2.00", bob to "5.00", al to "30.00")
        delivery = { _, _ -> 0.0 }

        assertIs<CollectionFundingError.Unsettled>(fund(Fiat("10.00", CurrencyCode.USD), Token.usdf).exceptionOrNull())

        assertEquals(2, submitted.size)
    }

    @Test
    fun `paying records the currency for next time and drops what was kept`() = runTest {
        store.put(requestId, FundingLegRecord(al.address, Mint.usdf, SwapId.generate(), List(32) { 3 }))

        funder.paid(requestId, jeffy)

        assertTrue(store.legs(requestId).isEmpty())
        assertEquals(jeffy.address, store.lastPaid().first())
    }
}
