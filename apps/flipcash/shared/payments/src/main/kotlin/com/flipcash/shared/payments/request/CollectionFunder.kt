package com.flipcash.shared.payments.request

import com.flipcash.app.tokens.CollectionFundingPlan
import com.flipcash.app.tokens.FundingBalance
import com.flipcash.app.tokens.FundingLeg
import com.flipcash.app.tokens.TokenCoordinator
import com.getcode.opencode.controllers.TransactionOperations
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.exchange.VerifiedFiat
import com.getcode.opencode.exchange.VerifiedFiatCalculator
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.core.errors.ComputeVerifiedFiatError
import com.getcode.opencode.model.core.errors.GetPendingSwapsError
import com.getcode.opencode.model.core.errors.GetSwapError
import com.getcode.opencode.model.core.errors.SwapError
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.transactions.SwapFundingSource
import com.getcode.opencode.model.transactions.SwapState
import com.getcode.opencode.repositories.SwapRepository
import com.getcode.opencode.utils.generate
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.base58
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Funds a collection payment by converting other balances into the paying currency, with no
 * top-up. Each leg is one of the swaps the Swap screen runs: into Dollars a sell, Dollars into a
 * community currency a buy with no fee, community into community a swap whose pool fee is taken
 * on-chain. Each leg is waited on until it finalises; the payment itself is the caller's.
 *
 * A leg's swap id and funding intent id are kept by payment request id before it is submitted, so
 * a retry after an unknown outcome asks the server about that swap first instead of converting
 * twice: it waits on a swap in flight, starts one the server never saw again under both kept ids,
 * funds one still CREATED under the funding id the server recorded for it rather than starting it
 * again, and mints fresh ids only once the server says the swap FAILED or was CANCELLED. A swap
 * funded on retry that still hasn't settled stops funding with
 * [CollectionFundingError.ConversionPending]. After legs land it plans again from the balances as
 * fetched, and converts more only if the balances cover the gap.
 */
@Singleton
class CollectionFunder @Inject constructor(
    private val transactions: TransactionOperations,
    private val swaps: SwapRepository,
    private val tokenCoordinator: TokenCoordinator,
    private val verifiedFiatCalculator: VerifiedFiatCalculator,
    private val exchange: Exchange,
    private val planner: CollectionFundingPlanner,
    private val store: CollectionFundingStore,
) {
    private val mutex = Mutex()

    /** How long to wait between balance fetches for a landed conversion to show; tests shorten it. */
    @androidx.annotation.VisibleForTesting
    internal var settleInterval: Duration = 1.seconds

    /**
     * Makes [owner]'s [paying] balance cover [amount] (in the request's currency). Fails with
     * [CollectionFundingError.Short] when it would take adding money, and runs no leg then.
     */
    suspend fun fund(requestId: ID, amount: Fiat, paying: Token, owner: AccountCluster): Result<Unit> =
        mutex.withLock {
            val created = settleStarted(requestId, owner).getOrElse { return@withLock Result.failure(it) }
            repeat(MAX_ROUNDS) {
                tokenCoordinator.update()
                val rate = exchange.rateFor(amount.currencyCode)
                    ?: return@withLock Result.failure(ComputeVerifiedFiatError.StaleRate())
                val balances = planner.balances(rate)
                val plan = planner.plan(amount, paying.address, balances, rate)
                // A short plan's legs describe the payment after a top-up, so none of them runs.
                if (!plan.payableWithoutTopUp) return@withLock Result.failure(CollectionFundingError.Short(plan))
                val legs = plan.legs.filter { it.conversion }
                // Anything still kept (never seen, or CREATED) is left for paying to clear: a swap
                // whose funding may yet land must not lose its ids to a later retry.
                if (legs.isEmpty()) return@withLock Result.success(Unit)
                val before = tokenCoordinator.currentBalance(paying.address)
                for (leg in legs) {
                    runLeg(requestId, leg, paying, balances, rate, owner, created)
                        .onFailure { return@withLock Result.failure(it) }
                }
                awaitBalanceAbove(paying.address, before).onFailure { return@withLock Result.failure(it) }
            }
            Result.failure(CollectionFundingError.Unsettled())
        }

    /**
     * Settles every swap an earlier attempt kept for [requestId], before a retry sizes a top-up:
     * a top-up sized while a kept swap's Dollars are still on their way charges for them twice.
     * Nothing kept returns false at once, touching neither the server nor the balances.
     *
     * Each kept swap goes by the server's state of it, as [settleStarted] has it, except that a
     * CREATED one is funded now under the funding id the server recorded (prices in [currency]),
     * whatever the plan. A swap that finalises here is settled once its `to` balance rises above
     * what it was before the wait. One already FINALIZED, FAILED or CANCELLED, or that ends while
     * waited on, is settled with nothing to wait on. One the server never saw stays kept: nothing
     * is on its way for it.
     *
     * True when any kept swap settled, so the plan must be read again. A swap still in flight, or a
     * balance that never rises, fails with [CollectionFundingError.ConversionPending] and keeps its
     * record: nothing should be ordered while it may still land.
     */
    suspend fun settle(requestId: ID, currency: CurrencyCode, owner: AccountCluster): Result<Boolean> =
        mutex.withLock {
            val kept = store.legs(requestId)
            if (kept.isEmpty()) return@withLock Result.success(false)
            tokenCoordinator.update()
            val before = kept.map { it.to }.distinct().associateWith { tokenCoordinator.currentBalance(it) }
            val landed = mutableListOf<FundingLegRecord>()
            var settled = false
            for (record in kept) {
                val found = swaps.getSwap(record.swapId, owner.authority.keyPair)
                val swap = found.getOrNull()
                if (swap == null) {
                    val error = found.exceptionOrNull()
                    if (error is GetSwapError.NotFound) continue
                    return@withLock Result.failure(error ?: IllegalStateException("getSwap returned nothing"))
                }
                val outcome = when (swap.state) {
                    SwapState.FINALIZED, SwapState.FAILED, SwapState.CANCELLED -> Result.success(SettleOutcome.Ended)
                    SwapState.CANCELLING -> awaitSettled(record, owner, SwapState.CANCELLED)
                    SwapState.CREATED -> {
                        val from = tokenCoordinator.cachedToken(record.from)
                            ?: return@withLock Result.failure(CollectionFundingError.UnknownCurrency())
                        val rate = exchange.rateFor(currency)
                            ?: return@withLock Result.failure(ComputeVerifiedFiatError.StaleRate())
                        fundRecorded(record, from, rate, owner).onFailure { return@withLock Result.failure(it) }
                        awaitSettled(record, owner, SwapState.FINALIZED)
                    }
                    else -> awaitSettled(record, owner, SwapState.FINALIZED)
                }.getOrElse { return@withLock Result.failure(it) }
                settled = true
                // A finalised swap's record stays until its Dollars show, so a retry still asks about it.
                if (outcome == SettleOutcome.Landed) landed += record else store.remove(requestId, record.swapId)
            }
            if (landed.isEmpty()) {
                if (settled) tokenCoordinator.update()
                return@withLock Result.success(settled)
            }
            for (mint in landed.map { it.to }.distinct()) {
                awaitBalanceAbove(mint, before.getValue(mint))
                    .onFailure { return@withLock Result.failure(CollectionFundingError.ConversionPending()) }
            }
            landed.forEach { store.remove(requestId, it.swapId) }
            Result.success(true)
        }

    /** The collection was paid in [paying]: its kept legs go, and [paying] is the next default. */
    suspend fun paid(requestId: ID, paying: Token) {
        store.clear(requestId)
        store.setLastPaid(paying.address)
    }

    /** Nothing is left to pay into [requestId], so nothing kept for it can be needed. */
    suspend fun forget(requestId: ID) = store.clear(requestId)

    /** How [amount] would be paid in [paying] from the balances as they stand; null with no rate. */
    fun plan(amount: Fiat, paying: Mint): CollectionFundingPlan? = planner.plan(amount, paying)

    /** What to order for [plan]'s top-up of [amount], in USD cents; null with no top-up or no rate. */
    fun topUpUsdCents(plan: CollectionFundingPlan, amount: Fiat): Long? {
        val rate = exchange.rateFor(amount.currencyCode) ?: return null
        return planner.topUpUsdCents(plan, rate)
    }

    /** The currency a collection was last paid in on this account, or null before the first. */
    fun lastPaid(): Flow<Mint?> = store.lastPaid()

    /**
     * Settles every leg a previous attempt kept, by the server's state of its swap:
     * - FINALIZED, FAILED or CANCELLED: the outcome is known, so the record goes. A failed pair
     *   gets fresh ids if the plan still needs it.
     * - Never seen: kept, so the next leg for the same pair starts it again under both ids.
     * - CREATED (started, funding never landed): kept and returned, so the next leg for the same
     *   pair funds it instead of starting it again.
     * - CANCELLING: waited on until the server cancels it. In flight (FUNDING, FUNDED, SUBMITTING):
     *   waited on until it finalises.
     * A wait that ends without an outcome stops funding with
     * [CollectionFundingError.ConversionPending] and keeps the record: no new id is minted while
     * the old swap may still settle.
     */
    private suspend fun settleStarted(requestId: ID, owner: AccountCluster): Result<Set<SwapId>> {
        val created = mutableSetOf<SwapId>()
        for (record in store.legs(requestId)) {
            val found = swaps.getSwap(record.swapId, owner.authority.keyPair)
            val swap = found.getOrNull()
            if (swap == null) {
                val error = found.exceptionOrNull()
                if (error is GetSwapError.NotFound) continue
                return Result.failure(error ?: IllegalStateException("getSwap returned nothing"))
            }
            when (swap.state) {
                SwapState.FINALIZED, SwapState.FAILED, SwapState.CANCELLED -> Unit
                SwapState.CREATED -> {
                    created += record.swapId
                    continue
                }
                SwapState.CANCELLING -> awaitOutcome(record, owner, SwapState.CANCELLED)
                    .onFailure { return Result.failure(it) }
                else -> awaitOutcome(record, owner, SwapState.FINALIZED)
                    .onFailure { return Result.failure(it) }
            }
            store.remove(requestId, record.swapId)
        }
        return Result.success(created)
    }

    /**
     * Polls [record]'s swap for [target]. Reaching it, or the server failing or cancelling the
     * swap, is an outcome; anything else is [CollectionFundingError.ConversionPending]. True when
     * the swap reached [target], false when the server failed or cancelled it first.
     */
    private suspend fun awaitOutcome(record: FundingLegRecord, owner: AccountCluster, target: SwapState): Result<Boolean> {
        val polled = transactions.pollSwapForState(record.swapId, owner, target)
        val error = polled.exceptionOrNull() ?: return Result.success(true)
        return if (error.endedSwap) Result.success(false) else Result.failure(CollectionFundingError.ConversionPending())
    }

    /** How a kept swap [settle] waited on came out. */
    private enum class SettleOutcome {
        /** It finalised, so its Dollars are on their way to its `to` balance. */
        Landed,

        /** It failed or was cancelled, or had finalised before: nothing more will arrive. */
        Ended,
    }

    /**
     * Polls [record]'s swap for [target], as [awaitOutcome] does, telling a finalised swap apart
     * from one that ended without delivering.
     */
    private suspend fun awaitSettled(record: FundingLegRecord, owner: AccountCluster, target: SwapState): Result<SettleOutcome> =
        awaitOutcome(record, owner, target).map { reached ->
            if (reached && target == SwapState.FINALIZED) SettleOutcome.Landed else SettleOutcome.Ended
        }

    private suspend fun runLeg(
        requestId: ID,
        leg: FundingLeg,
        paying: Token,
        balances: List<FundingBalance>,
        rate: Rate,
        owner: AccountCluster,
        created: Set<SwapId>,
    ): Result<Unit> {
        val from = tokenCoordinator.cachedToken(Mint(leg.source))
            ?: return Result.failure(CollectionFundingError.UnknownCurrency())
        val kept = store.legs(requestId).firstOrNull { it.from == from.address && it.to == paying.address }
        if (kept != null && kept.swapId in created) return fundCreated(requestId, kept, from, rate, owner)
        val record = kept
            ?: FundingLegRecord(
                from = from.address,
                to = paying.address,
                swapId = SwapId.generate(),
                fundingIntentId = PublicKey.generate().bytes,
            ).also { store.put(requestId, it) }
        val held = balances.first { it.id == leg.source }
        val amount = price(leg, from, drains = leg.soldMicros >= held.fiatMicros, rate)
            .getOrElse { return Result.failure(it) }
        val source = SwapFundingSource.SubmitIntent(record.fundingIntentId)
        val submitted = when {
            paying.address == Mint.usdf -> transactions.sell(
                owner = owner, amount = amount, of = from, swapId = record.swapId, source = source,
            )
            from.address == Mint.usdf -> transactions.buy(
                owner = owner, amount = amount, feeAmount = null, swapId = record.swapId, of = paying, source = source,
            )
            else -> transactions.swap(
                owner = owner, amount = amount, feeAmount = null, from = from, to = paying,
                swapId = record.swapId, source = source,
            )
        }
        // An unknown outcome keeps the record, so the retry asks the server about this swap first.
        submitted.onFailure { return Result.failure(it) }
        return awaitLeg(requestId, record, owner, retried = kept != null)
    }

    /**
     * Funds [record]'s swap, which the server has in CREATED, under the funding id the server
     * recorded for it, never starting it again: a second start isn't deduped, and funding under
     * another intent id could pay the swap twice. The server's funding id wins over the kept one,
     * and the amount is what the server recorded. A swap missing from the pending list, or listed
     * in another state, has moved on since it was reported CREATED, so it is waited on.
     */
    private suspend fun fundCreated(
        requestId: ID,
        record: FundingLegRecord,
        from: Token,
        rate: Rate,
        owner: AccountCluster,
    ): Result<Unit> {
        fundRecorded(record, from, rate, owner).onFailure { return Result.failure(it) }
        return awaitLeg(requestId, record, owner, retried = true)
    }

    /**
     * Funds [record]'s CREATED swap under the funding id and amount the server recorded for it.
     * Does nothing when the server no longer lists it CREATED: it has moved on, so the caller
     * waits on it either way. An unknown outcome keeps the record: the retry finds the swap
     * CREATED or in flight again.
     */
    private suspend fun fundRecorded(record: FundingLegRecord, from: Token, rate: Rate, owner: AccountCluster): Result<Unit> {
        val pending = swaps.getPendingSwaps(owner.authority.keyPair).getOrElse { error ->
            if (error is GetPendingSwapsError.NotFound) emptyList() else return Result.failure(error)
        }
        val recorded = pending
            .firstOrNull { it.swapId == record.swapId && it.state == SwapState.CREATED }
            ?.verifiedMetadata
        val fundingId = (recorded?.fundingSource as? SwapFundingSource.SubmitIntent)?.id
        if (recorded == null || fundingId == null) return Result.success(Unit)
        val quarks = recorded.swapAmount.quarks + (recorded.feeAmount?.quarks ?: 0L)
        val amount = verifiedFiatCalculator.computeForQuarks(quarks, from, rate).getOrElse { return Result.failure(it) }
        return transactions.fundSwap(owner, from, amount, fundingId)
    }

    /**
     * Waits on [record]'s swap to finalise. A swap the server failed or cancelled drops its record,
     * so a retry may mint anew. One that hasn't settled keeps it; when [retried], a later retry
     * must wait on it, so funding stops on [CollectionFundingError.ConversionPending].
     */
    private suspend fun awaitLeg(requestId: ID, record: FundingLegRecord, owner: AccountCluster, retried: Boolean): Result<Unit> {
        transactions.pollSwapForState(record.swapId, owner, SwapState.FINALIZED).onFailure { error ->
            if (error.endedSwap) {
                store.remove(requestId, record.swapId)
                return Result.failure(error)
            }
            return Result.failure(if (retried) CollectionFundingError.ConversionPending() else error)
        }
        store.remove(requestId, record.swapId)
        return Result.success(Unit)
    }

    /** The poll ended because the server failed or cancelled the swap, so it can no longer settle. */
    private val Throwable.endedSwap: Boolean
        get() = this is SwapError.Terminal

    /**
     * The amount [leg] sells, in [from]. A drained leg sells every quark held rather than the
     * quarks its fiat value prices to, which can land a few quarks short and leave dust.
     */
    private suspend fun price(leg: FundingLeg, from: Token, drains: Boolean, rate: Rate): Result<VerifiedFiat> {
        val priced = verifiedFiatCalculator.compute(
            amount = Fiat(leg.soldMicros, rate.currency),
            token = from,
            balance = tokenCoordinator.balanceForToken(from),
            rate = rate,
        ).getOrElse { return Result.failure(it) }
        if (!drains || from.address == Mint.usdf) return Result.success(priced)
        val quarks = tokenCoordinator.heldQuarksForToken(from.address).first()
            ?: return Result.failure(CollectionFundingError.Unsettled())
        return Result.success(
            priced.copy(localFiat = priced.localFiat.copy(underlyingTokenAmount = Fiat(quarks, CurrencyCode.USD))),
        )
    }

    private suspend fun awaitBalanceAbove(mint: Mint, before: Fiat): Result<Unit> {
        repeat(SETTLE_CHECKS) { check ->
            if (check > 0) delay(settleInterval)
            tokenCoordinator.update()
            if (tokenCoordinator.currentBalance(mint).quarks > before.quarks) return Result.success(Unit)
        }
        return Result.failure(CollectionFundingError.Unsettled())
    }

    private companion object {
        /** Plan, convert, fetch: a short delivery gets this many more rounds before giving up. */
        const val MAX_ROUNDS = 3
        const val SETTLE_CHECKS = 5
    }
}

/** Why [CollectionFunder.fund] stopped before the payment. */
sealed class CollectionFundingError(message: String) : Exception(message) {
    /** Conversions can't cover the payment; it takes adding money first. */
    class Short(val plan: CollectionFundingPlan) : CollectionFundingError("conversions can't cover the payment")

    /** A conversion finalised but the paying balance didn't move, so nothing more is converted. */
    class Unsettled : CollectionFundingError("a conversion's result hasn't reached the balance")

    /**
     * A conversion kept from an earlier attempt still hasn't settled, after waiting on it or
     * funding it on retry, or its Dollars haven't reached the balance. Nothing new is converted or
     * ordered while it may still land; try again shortly.
     */
    class ConversionPending : CollectionFundingError("a conversion is still pending")

    /** The plan named a currency the wallet no longer knows. */
    class UnknownCurrency : CollectionFundingError("a funding currency is no longer known")
}
