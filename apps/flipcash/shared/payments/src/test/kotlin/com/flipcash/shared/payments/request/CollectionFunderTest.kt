package com.flipcash.shared.payments.request

import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.libs.currency.math.Curves
import com.flipcash.libs.currency.math.Estimator
import com.flipcash.app.tokens.FundingLeg
import com.getcode.opencode.controllers.TransactionOperations
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.exchange.VerifiedFiat
import com.getcode.opencode.exchange.VerifiedFiatCalculator
import com.getcode.opencode.internal.manager.VerifiedState
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.model.core.errors.ComputeVerifiedFiatError
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.core.errors.GetPendingSwapsError
import com.getcode.opencode.model.core.errors.GetSwapError
import com.getcode.opencode.model.core.errors.SwapError
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LaunchpadMetadata
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.async
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
    private val state = mockk<VerifiedState>(relaxed = true)

    private val jeffy = token(11)
    private val bob = token(12)
    private val al = token(13)

    /** The supply GetMints reports for Moony, which its balance is valued at. */
    private var getMintsSupply = 0L

    /** The supply in the signed reserve state, which a Moony payment is priced at. */
    private var signedSupply = 0L

    /** Quarks each leg into Moony delivers, in order; Moony's balance is held in quarks, not micros. */
    private val moonyBuys = ArrayDeque<Long>()

    /** Runs on each balance refresh, before the pending results land. */
    private var onRefresh: () -> Unit = {}
    private var refreshes = 0
    private val moonyLaunchpad = mockk<LaunchpadMetadata>(relaxed = true) {
        every { currentCirculatingSupplyQuarks } answers { getMintsSupply }
        every { sellFeeBps } returns 100
    }

    /** A launchpad currency priced on the real curve, unlike the others, whose quarks are micros. */
    private val moony = mockk<Token>(relaxed = true) {
        every { this@mockk.address } returns Mint(List(32) { 14 })
        every { decimals } returns 10
        every { launchpadMetadata } returns moonyLaunchpad
    }
    private val tokens = listOf(Token.usdf, jeffy, bob, al, moony).associateBy { it.address }

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
    ).also {
        it.settleInterval = Duration.ZERO
        it.log = { message, _ -> logged += message }
    }

    /** Every line the funder logged, in order. */
    private val logged = mutableListOf<String>()

    /** The payability checks the funder logged, each ending in its verdict. */
    private val gateChecks get() = logged.filter { it.startsWith("collection gate") }

    /** Every leg submitted, in order: from, to, swap id, funding intent id, priced amount. */
    private val submitted = mutableListOf<Submitted>()
    private val computed = mutableListOf<Triple<Fiat, Token, Rate>>()

    private data class Submitted(val from: Mint, val to: Mint, val swapId: SwapId, val fundingId: List<Byte>, val amount: VerifiedFiat)

    /** The leg each source is running under the plan of its round, for the fake's delivery. */
    private val legs = mutableMapOf<Mint, FundingLeg>()

    private val usd = Rate(1.0, CurrencyCode.USD)
    private val eur = Rate(0.5, CurrencyCode.EUR)

    init {
        // Moony is priced on the real curve.
        runBlocking { Curves.initializeFromSourceTree() }
        // A matcher needs a placeholder swap id, and a random one isn't 32 bytes.
        registerInstanceFactory { SwapId.generate() }
        every { exchange.rateFor(CurrencyCode.USD) } returns usd
        every { exchange.rateFor(CurrencyCode.EUR) } returns eur
        every { tokenCoordinator.cachedTokens() } answers { tokens.values.toList() }
        every { tokenCoordinator.cachedToken(any()) } answers { tokens[firstArg()] }
        every { tokenCoordinator.currentBalance(any()) } answers { valued(firstArg()) }
        every { tokenCoordinator.balanceForToken(any<Token>()) } answers { valued(firstArg<Token>().address) }
        every { tokenCoordinator.heldQuarksForToken(any()) } answers { flowOf(balances[firstArg()]) }
        coEvery { tokenCoordinator.update() } answers {
            refreshes++
            onRefresh()
            pending.forEach { (mint, micros) -> balances[mint] = (balances[mint] ?: 0L) + micros }
            pending.clear()
        }
        every { state.reserveSupplyFromBonding } answers { signedSupply }
        coEvery { calculator.compute(any(), any(), any(), any(), any()) } answers {
            val amount = firstArg<Fiat>()
            val token = secondArg<Token>()
            val rate = arg<Rate>(3)
            computed += Triple(amount, token, rate)
            // As the real calculator does: the amount in USD, capped at the balance it's given.
            val usd = (amount.quarks / rate.fx).roundToLong()
            val capped = thirdArg<Fiat?>()?.let { balance ->
                minOf(if (balance.currencyCode == CurrencyCode.USD) balance.quarks else (balance.quarks / rate.fx).roundToLong(), usd)
            } ?: usd
            val local = if (token == moony) {
                // Quarks bought for the value at the signed supply, valued back as a sell there.
                val quarks = Estimator.valueExchangeAsQuarks(capped, signedSupply, 6).getOrThrow().quarks.toLong()
                LocalFiat(Fiat(quarks, CurrencyCode.USD), Fiat.tokenBalance(quarks, moony, signedSupply).convertingTo(rate), rate, token.address)
            } else {
                LocalFiat(Fiat(capped, CurrencyCode.USD), Fiat((capped * rate.fx).roundToLong(), amount.currencyCode), rate, token.address)
            }
            Result.success(VerifiedFiat(local, state))
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
        val delivered = if (leg.to == moony.address) {
            moonyBuys.removeFirstOrNull() ?: 0L
        } else {
            (planned.deliveredMicros * delivery(leg.from, round) / fx).roundToLong()
        }
        pending[leg.to] = (pending[leg.to] ?: 0L) + delivered
    }

    /** A balance in USD micros: Moony's quarks valued as a sell at the GetMints supply, as the wallet does. */
    private fun valued(mint: Mint): Fiat {
        val held = balances[mint] ?: 0L
        return if (mint == moony.address) Fiat.tokenBalance(held, moony, getMintsSupply) else Fiat(held, CurrencyCode.USD)
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

        val (amount, token, rate) = computed.first()
        assertEquals(CurrencyCode.EUR, amount.currencyCode)
        assertEquals(Fiat("10.00", CurrencyCode.EUR), amount)
        assertEquals(Token.usdf, token)
        assertEquals(eur, rate)
        // Then the payment itself is priced, in the paying currency, as paying will price it.
        assertEquals(Triple(Fiat("10.00", CurrencyCode.EUR), jeffy, eur), computed[1])
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
    fun `a kept CREATED swap under the minimum is dropped, not funded, and its leg starts afresh`() = runTest {
        val kept = keepUnsettledLeg()
        serverSays(kept, SwapState.CREATED)
        serverHasPending(kept, kept.fundingIntentId, swapQuarks = 396L, feeQuarks = 0L)
        legsSettle()

        assertTrue(fund(Fiat("10.00", CurrencyCode.USD), Token.usdf).isSuccess)

        coVerify(exactly = 0) { transactions.fundSwap(any(), any(), any(), any()) }
        assertEquals(2, submitted.size)
        assertNotEquals(kept.swapId, submitted[1].swapId)
        assertNotEquals(kept.fundingIntentId, submitted[1].fundingId)
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
    fun `a leg that lands short converts nothing more, though the balances would cover it`() = runTest {
        hold(Token.usdf to "2.00", bob to "5.00", al to "30.00")
        // Al delivers half its planned amount; what Al has left could cover the rest.
        delivery = { from, _ -> if (from == al.address) 0.5 else 1.0 }

        val result = fund(Fiat("10.00", CurrencyCode.USD), Token.usdf)

        assertIs<CollectionFundingError.ConversionPending>(result.exceptionOrNull())
        assertEquals(listOf(bob.address, al.address), submitted.map { it.from })
        assertEquals(GATE_CHECKS, gateChecks.size)
        assertTrue(gateChecks.all { it.endsWith("-> wait") })
    }

    @Test
    fun `a leg that lands short with nothing left to convert stops on the pending state, not short`() = runTest {
        hold(Token.usdf to "2.00", al to "8.20")
        delivery = { _, _ -> 0.5 }

        // Short is the next tap's to find, from the balances as they are then.
        assertIs<CollectionFundingError.ConversionPending>(fund(Fiat("10.00", CurrencyCode.USD), Token.usdf).exceptionOrNull())

        assertEquals(1, submitted.size)
    }

    @Test
    fun `a finalised leg whose result never reaches the balance converts nothing more`() = runTest {
        hold(Token.usdf to "2.00", bob to "5.00", al to "30.00")
        delivery = { _, _ -> 0.0 }

        assertIs<CollectionFundingError.ConversionPending>(fund(Fiat("10.00", CurrencyCode.USD), Token.usdf).exceptionOrNull())

        assertEquals(2, submitted.size)
        // One refresh to plan from, then one per check.
        coVerify(exactly = 1 + GATE_CHECKS) { tokenCoordinator.update() }
    }

    @Test
    fun `a leg under the minimum transfer is raised to it when its source holds that much`() = runTest {
        hold(Token.usdf to "9.995", al to "20.00")

        assertTrue(fund(Fiat("10.00", CurrencyCode.USD), Token.usdf).isSuccess)

        assertTrue(legs.getValue(al.address).soldMicros < MINIMUM_TRANSFER, "planned under a cent")
        val leg = submitted.single()
        assertEquals(al.address, leg.from)
        assertEquals(MINIMUM_TRANSFER, leg.amount.localFiat.nativeAmount.quarks)
        assertEquals(Fiat(MINIMUM_TRANSFER, CurrencyCode.USD), computed.first().first)
    }

    @Test
    fun `a leg raised to the minimum drains a source holding exactly that much`() = runTest {
        hold(Token.usdf to "9.995", al to "0.01")
        // A few more quarks than the cent they're valued at, so a drain is told apart.
        val mint = al.address
        every { tokenCoordinator.heldQuarksForToken(mint) } returns flowOf(10_007L)

        assertTrue(fund(Fiat("10.00", CurrencyCode.USD), Token.usdf).isSuccess)

        assertTrue(legs.getValue(al.address).soldMicros < MINIMUM_TRANSFER, "planned under a cent, short of a drain")
        assertEquals(10_007L, submitted.single().amount.localFiat.underlyingTokenAmount.quarks)
    }

    @Test
    fun `a leg under the minimum from a source holding less fails before any leg runs`() = runTest {
        // Al's 0.8 cents isn't dust to the planner, but the server won't move less than a cent.
        hold(Token.usdf to "9.00", bob to "2.00", al to "0.008")

        val result = fund(Fiat("10.00", CurrencyCode.USD), Token.usdf)

        assertIs<ComputeVerifiedFiatError.AmountBelowMinimum>(result.exceptionOrNull())
        assertEquals(8_000L, legs.getValue(al.address).soldMicros, "the plan drains Al")
        assertTrue(submitted.isEmpty(), "Bob's leg doesn't run either")
        assertTrue(store.legs(requestId).isEmpty())
    }

    // settle: a retry that would top up settles what was kept first (spec, "Adding money and
    // recovery" item 3), so the top-up is sized from balances the kept swaps have already reached.

    private suspend fun settle(): Result<Boolean> = funder.settle(requestId, CurrencyCode.USD, owner)

    @Test
    fun `settling with nothing kept asks the server nothing and fetches no balance`() = runTest {
        assertEquals(false, settle().getOrThrow())

        coVerify(exactly = 0) { swaps.getSwap(any(), any()) }
        coVerify(exactly = 0) { swaps.getPendingSwaps(any()) }
        coVerify(exactly = 0) { tokenCoordinator.update() }
    }

    @Test
    fun `settling funds a kept CREATED swap under its recorded id and returns once its Dollars show`() = runTest {
        val kept = keepUnsettledLeg()
        serverSays(kept, SwapState.CREATED)
        val recordedId = PublicKey.generate().bytes
        serverHasPending(kept, recordedId)
        legsSettle()

        assertEquals(true, settle().getOrThrow())

        assertEquals(recordedId, funded.single().fundingId)
        assertEquals(1, submitted.size, "never started again")
        assertEquals(Fiat("10.00", CurrencyCode.USD).quarks, balances[Mint.usdf], "the $8.00 reached the balance")
        assertTrue(store.legs(requestId).isEmpty())
    }

    @Test
    fun `settling drops a kept CREATED swap whose recorded amount is under the minimum, never funding it`() = runTest {
        val kept = keepUnsettledLeg()
        serverSays(kept, SwapState.CREATED)
        serverHasPending(kept, kept.fundingIntentId, swapQuarks = 396L, feeQuarks = 0L)

        assertEquals(true, settle().getOrThrow())

        coVerify(exactly = 0) { transactions.fundSwap(any(), any(), any(), any()) }
        assertTrue(store.legs(requestId).isEmpty())
        // Only the first attempt's poll: nothing waits on a swap that will never move.
        coVerify(exactly = 1) { transactions.pollSwapForState(any(), owner, any(), any(), any()) }
    }

    @Test
    fun `settling drops a kept CREATED swap too small to price`() = runTest {
        val kept = keepUnsettledLeg()
        serverSays(kept, SwapState.CREATED)
        serverHasPending(kept, kept.fundingIntentId, swapQuarks = 3L, feeQuarks = 0L)
        coEvery { calculator.computeForQuarks(any(), any(), any()) } returns
            Result.failure(ComputeVerifiedFiatError.AmountBelowMinimum())

        assertEquals(true, settle().getOrThrow())

        coVerify(exactly = 0) { transactions.fundSwap(any(), any(), any(), any()) }
        assertTrue(store.legs(requestId).isEmpty())
    }

    @Test
    fun `settling waits on a kept swap in flight until its balance rises`() = runTest {
        val kept = keepUnsettledLeg()
        serverSays(kept, SwapState.FUNDING)
        coEvery { transactions.pollSwapForState(kept.swapId, owner, SwapState.FINALIZED, any(), any()) } returns
            Result.success(mockk())
        // The swap finalises before its Dollars reach the balance: they show on the third fetch.
        var fetches = 0
        coEvery { tokenCoordinator.update() } answers {
            if (++fetches == 3) balances[Mint.usdf] = Fiat("10.00", CurrencyCode.USD).quarks
        }

        assertEquals(true, settle().getOrThrow())

        assertEquals(3, fetches)
        assertTrue(store.legs(requestId).isEmpty())
    }

    @Test
    fun `a finalised kept swap whose Dollars never show stops on the pending state and stays kept`() = runTest {
        val kept = keepUnsettledLeg()
        serverSays(kept, SwapState.FUNDING)
        coEvery { transactions.pollSwapForState(kept.swapId, owner, SwapState.FINALIZED, any(), any()) } returns
            Result.success(mockk())

        assertIs<CollectionFundingError.ConversionPending>(settle().exceptionOrNull())

        assertEquals(kept, store.legs(requestId).single())
    }

    @Test
    fun `a kept swap still in flight after waiting stops settling on the pending state and stays kept`() = runTest {
        val kept = keepUnsettledLeg()
        serverSays(kept, SwapState.FUNDING)

        assertIs<CollectionFundingError.ConversionPending>(settle().exceptionOrNull())

        assertEquals(1, submitted.size)
        assertEquals(kept, store.legs(requestId).single())
    }

    @Test
    fun `a funded CREATED swap that still hasn't settled stops on the pending state and stays kept`() = runTest {
        val kept = keepUnsettledLeg()
        serverSays(kept, SwapState.CREATED)
        serverHasPending(kept, kept.fundingIntentId)

        assertIs<CollectionFundingError.ConversionPending>(settle().exceptionOrNull())

        assertEquals(1, funded.size)
        assertEquals(kept, store.legs(requestId).single())
    }

    @Test
    fun `a kept swap the server failed or cancelled is dropped, with no balance to wait on`() = runTest {
        for (ended in listOf(SwapState.FAILED, SwapState.CANCELLED)) {
            store.clear(requestId)
            val record = FundingLegRecord(al.address, Mint.usdf, SwapId.generate(), List(32) { 3 })
            store.put(requestId, record)
            serverSays(record, ended)

            assertEquals(true, settle().getOrThrow(), "$ended")

            assertTrue(store.legs(requestId).isEmpty(), "$ended")
        }
        coVerify(exactly = 0) { transactions.pollSwapForState(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a funded CREATED swap the server then fails is dropped, with no balance to wait on`() = runTest {
        val kept = keepUnsettledLeg()
        serverSays(kept, SwapState.CREATED)
        serverHasPending(kept, kept.fundingIntentId)
        coEvery { transactions.pollSwapForState(kept.swapId, owner, SwapState.FINALIZED, any(), any()) } returns
            Result.failure(SwapError.Terminal(SwapState.FAILED))

        assertEquals(true, settle().getOrThrow())

        assertTrue(store.legs(requestId).isEmpty())
    }

    @Test
    fun `a kept swap that already finalised is dropped, with no balance to wait on`() = runTest {
        val record = FundingLegRecord(al.address, Mint.usdf, SwapId.generate(), List(32) { 3 })
        store.put(requestId, record)
        serverSays(record, SwapState.FINALIZED)

        assertEquals(true, settle().getOrThrow())

        assertTrue(store.legs(requestId).isEmpty())
        coVerify(exactly = 0) { transactions.pollSwapForState(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a kept swap the server never saw stays kept, with nothing on its way`() = runTest {
        val record = FundingLegRecord(al.address, Mint.usdf, SwapId.generate(), List(32) { 3 })
        store.put(requestId, record)
        coEvery { swaps.getSwap(record.swapId, any()) } returns Result.failure(GetSwapError.NotFound())

        assertEquals(false, settle().getOrThrow())

        assertEquals(record, store.legs(requestId).single())
        coVerify(exactly = 0) { transactions.pollSwapForState(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a failed swap lookup stops settling and keeps what was kept`() = runTest {
        val record = FundingLegRecord(al.address, Mint.usdf, SwapId.generate(), List(32) { 3 })
        store.put(requestId, record)
        val offline = IllegalStateException("offline")
        coEvery { swaps.getSwap(record.swapId, any()) } returns Result.failure(offline)

        assertEquals(offline, settle().exceptionOrNull())

        assertEquals(record, store.legs(requestId).single())
    }

    @Test
    fun `paying records the currency for next time and drops what was kept`() = runTest {
        store.put(requestId, FundingLegRecord(al.address, Mint.usdf, SwapId.generate(), List(32) { 3 }))

        funder.paid(requestId, jeffy)

        assertTrue(store.legs(requestId).isEmpty())
        assertEquals(jeffy.address, store.lastPaid().first())
    }

    @Test
    fun `a conversion submission with no answer fails at the deadline and keeps its record`() = runTest {
        hold(Token.usdf to "2.00", al to "20.00")
        coEvery { transactions.sell(owner, any(), al, any(), any()) } coAnswers {
            submit(al.address, Mint.usdf, arg(3), arg(4), arg(1))
            awaitCancellation()
        }

        val result = async { fund(Fiat("10.00", CurrencyCode.USD), Token.usdf) }
        advanceTimeBy(59_999)
        assertTrue(result.isActive)
        advanceTimeBy(1)
        runCurrent()

        assertTrue(result.await().exceptionOrNull() is TimeoutException)
        // The swap may have reached the server, so the retry asks about it before converting again.
        assertEquals(submitted.single().swapId, store.legs(requestId).single().swapId)
    }

    @Test
    fun `a conversion poll with no answer ends after its budget and keeps the record`() = runTest {
        hold(Token.usdf to "2.00", al to "20.00")
        coEvery { transactions.pollSwapForState(any(), owner, SwapState.FINALIZED, any(), any()) } coAnswers {
            awaitCancellation()
        }

        val result = async { fund(Fiat("10.00", CurrencyCode.USD), Token.usdf) }
        // The poll's own 90 attempts a second apart, plus one call's deadline.
        advanceTimeBy(149_999)
        assertTrue(result.isActive)
        advanceTimeBy(1)
        runCurrent()

        assertTrue(result.await().exceptionOrNull() is TimeoutException)
        assertEquals(1, store.legs(requestId).size)
    }

    @Test
    fun `a balance refresh with no answer doesn't hold funding`() = runTest {
        hold(Token.usdf to "20.00")
        coEvery { tokenCoordinator.update() } coAnswers { awaitCancellation() }

        val result = async { fund(Fiat("10.00", CurrencyCode.USD), Token.usdf) }
        advanceTimeBy(60_000)
        runCurrent()

        // The balances as last read already cover it, so nothing converts and funding goes on.
        assertTrue(result.await().isSuccess)
        assertTrue(submitted.isEmpty())
    }

    @Test
    fun `the fake values Moony as the device did`() {
        // The trace's reads: quarks before and after the buy, at the pre-buy and fresh supply.
        assertEquals(6_000_177L, Fiat.tokenBalance(HELD_BEFORE_BUY, moony, PRE_BUY_SUPPLY).quarks)
        assertEquals(9_999_604L, Fiat.tokenBalance(HELD_AFTER_BUY, moony, PRE_BUY_SUPPLY).quarks)
        assertEquals(10_000_000L, Fiat.tokenBalance(HELD_AFTER_BUY, moony, FRESH_SUPPLY).quarks)
    }

    @Test
    fun `a Moony buy read back at the pre-buy supply starts no second leg`() = runTest {
        // Seeker, 2026-11-22: $10 paid in Moony from $6.000177 of Moony plus Dollars.
        getMintsSupply = PRE_BUY_SUPPLY
        signedSupply = PRE_BUY_SUPPLY
        balances[moony.address] = HELD_BEFORE_BUY
        hold(Token.usdf to "5.00")
        // The buy lands its quarks; GetMints still reports the supply from before it.
        moonyBuys += HELD_AFTER_BUY - HELD_BEFORE_BUY

        val result = fund(Fiat("10.00", CurrencyCode.USD), moony)

        val legs = submitted.map { it.from to it.amount.localFiat.underlyingTokenAmount.quarks }
        assertEquals(listOf(Mint.usdf to 3_999_823L), legs)
        assertTrue(result.isSuccess, "funding failed: ${result.exceptionOrNull()}")
        // Still at the pre-buy supply, $10 prices 397 micros short: within half a cent, so it pays.
        assertEquals(listOf("pay"), gateChecks.map { it.substringAfterLast("-> ") })
        assertTrue(gateChecks.single().contains("priced=1055025844925 quarks / 9999603"), gateChecks.single())
    }

    @Test
    fun `at the trace's fresh supply the payment prices whole`() = runTest {
        // A $4 buy whose result shows with the fresh supply on the first check.
        getMintsSupply = PRE_BUY_SUPPLY
        signedSupply = PRE_BUY_SUPPLY
        balances[moony.address] = HELD_BEFORE_BUY
        hold(Token.usdf to "5.00")
        moonyBuys += HELD_AFTER_BUY - HELD_BEFORE_BUY
        onRefresh = { if (submitted.isNotEmpty()) freshSupply(FRESH_SUPPLY) }

        assertTrue(fund(Fiat("10.00", CurrencyCode.USD), moony).isSuccess)

        // $10 prices to 92,839 fewer quarks than held, and to one micro under $10.
        val priced = buyQuarks(10_000_000L, FRESH_SUPPLY)
        assertEquals(1_055_025_783_131L, priced)
        assertTrue(priced <= HELD_AFTER_BUY)
        assertEquals(9_999_999L, Fiat.tokenBalance(priced, moony, FRESH_SUPPLY).quarks)
        assertEquals(listOf("pay"), gateChecks.map { it.substringAfterLast("-> ") })
        assertTrue(gateChecks.single().contains("held=$HELD_AFTER_BUY quarks"), gateChecks.single())
        assertTrue(gateChecks.single().contains("signed supply=$FRESH_SUPPLY"), gateChecks.single())
    }

    @Test
    fun `a stale read short by more than half a cent waits until a refresh shows the fresh supply`() = runTest {
        val (held, fresh) = sixtyPlusForty()
        var reads = 0
        onRefresh = { if (submitted.isNotEmpty() && ++reads > 3) freshSupply(fresh) }

        assertTrue(fund(Fiat("100.00", CurrencyCode.USD), moony).isSuccess)

        // Read at the pre-buy supply the new quarks fall more than a cent short: replanning from
        // that read would have converted more, and the server would have taken it.
        val staleGap = 100_000_000L - Fiat.tokenBalance(held, moony, PRE_BUY_SUPPLY).quarks
        assertTrue(staleGap > MINIMUM_TRANSFER, "stale gap $staleGap")
        assertEquals(1, submitted.size)
        assertEquals(listOf("wait", "wait", "wait", "pay"), gateChecks.map { it.substringAfterLast("-> ") })
    }

    @Test
    fun `a signed state from before the buy prices more quarks than held, so it waits`() = runTest {
        val (_, fresh) = sixtyPlusForty()
        var reads = 0
        // GetMints catches up first; the signed reserve state a check later.
        onRefresh = {
            if (submitted.isNotEmpty()) {
                getMintsSupply = fresh
                if (++reads > 1) signedSupply = fresh
            }
        }

        assertTrue(fund(Fiat("100.00", CurrencyCode.USD), moony).isSuccess)

        assertEquals(1, submitted.size)
        assertEquals(listOf("wait", "pay"), gateChecks.map { it.substringAfterLast("-> ") })
    }

    @Test
    fun `a read that stays stale stops on the pending state without a second leg`() = runTest {
        sixtyPlusForty()

        val result = fund(Fiat("100.00", CurrencyCode.USD), moony)

        assertIs<CollectionFundingError.ConversionPending>(result.exceptionOrNull())
        assertEquals(1, submitted.size)
        assertEquals(GATE_CHECKS, gateChecks.size)
        assertTrue(gateChecks.all { it.endsWith("-> wait") })
    }

    private fun buyQuarks(micros: Long, supply: Long): Long =
        Estimator.valueExchangeAsQuarks(micros, supply, 6).getOrThrow().quarks.toLong()

    private fun freshSupply(supply: Long) {
        getMintsSupply = supply
        signedSupply = supply
    }

    /**
     * $60 of Moony at the trace's pre-buy supply plus Dollars, for a $100 payment: the plan buys
     * about $40 of Moony, whose quarks value at $100 at the supply after the buy. Returns the
     * quarks held after the buy and that supply.
     */
    private fun sixtyPlusForty(): Pair<Long, Long> {
        getMintsSupply = PRE_BUY_SUPPLY
        signedSupply = PRE_BUY_SUPPLY
        val before = buyQuarks(60_000_000L, PRE_BUY_SUPPLY)
        balances[moony.address] = before
        hold(Token.usdf to "40.01")
        // The quarks a buy delivers so the holding values at $100 at the supply it leaves.
        var bought = buyQuarks(40_000_000L, PRE_BUY_SUPPLY)
        repeat(5) { bought = buyQuarks(100_000_000L, PRE_BUY_SUPPLY + bought) - before }
        moonyBuys += bought
        return (before + bought) to (PRE_BUY_SUPPLY + bought)
    }

    private companion object {
        /** The server's minimum transfer in USD: a cent. */
        const val MINIMUM_TRANSFER = 10_000L

        /** How many times funding checks the payment prices whole before giving up. */
        const val GATE_CHECKS = 20

        /** Moony's supply from bonding as GetMints reported it after the buy, from before it. */
        const val PRE_BUY_SUPPLY = 25_639_941_291_908_912L

        /** The supply about 14 s later: [PRE_BUY_SUPPLY] plus exactly the quarks bought. */
        const val FRESH_SUPPLY = 25_640_363_264_428_011L

        const val HELD_BEFORE_BUY = 633_053_356_871L
        const val HELD_AFTER_BUY = 1_055_025_875_970L
    }
}
