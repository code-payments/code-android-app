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
import com.getcode.opencode.model.core.errors.GetSwapError
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
 * a retry after an unknown outcome waits on that swap, or resubmits under the same ids when the
 * server never saw it, instead of converting twice. After legs land it plans again from the
 * balances as fetched, and converts more only if the balances cover the gap.
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
            settleStarted(requestId, owner).onFailure { return@withLock Result.failure(it) }
            repeat(MAX_ROUNDS) {
                tokenCoordinator.update()
                val rate = exchange.rateFor(amount.currencyCode)
                    ?: return@withLock Result.failure(ComputeVerifiedFiatError.StaleRate())
                val balances = planner.balances(rate)
                val plan = planner.plan(amount, paying.address, balances)
                // A short plan's legs describe the payment after a top-up, so none of them runs.
                if (!plan.payableWithoutTopUp) return@withLock Result.failure(CollectionFundingError.Short(plan))
                val legs = plan.legs.filter { it.conversion }
                if (legs.isEmpty()) {
                    // Whatever is still kept never reached the server: nothing to wait on.
                    store.clear(requestId)
                    return@withLock Result.success(Unit)
                }
                val before = tokenCoordinator.currentBalance(paying.address)
                for (leg in legs) {
                    runLeg(requestId, leg, paying, balances, rate, owner)
                        .onFailure { return@withLock Result.failure(it) }
                }
                awaitBalanceAbove(paying.address, before).onFailure { return@withLock Result.failure(it) }
            }
            Result.failure(CollectionFundingError.Unsettled())
        }

    /** The collection was paid in [paying]: its kept legs go, and [paying] is the next default. */
    suspend fun paid(requestId: ID, paying: Token) {
        store.clear(requestId)
        store.setLastPaid(paying.address)
    }

    /** Nothing is left to pay into [requestId], so nothing kept for it can be needed. */
    suspend fun forget(requestId: ID) = store.clear(requestId)

    /**
     * Waits out every leg a previous attempt submitted. One that finalised, failed or was
     * cancelled is dropped; one the server never saw is kept, so the next leg for the same pair
     * reuses its ids.
     */
    private suspend fun settleStarted(requestId: ID, owner: AccountCluster): Result<Unit> {
        for (record in store.legs(requestId)) {
            val found = swaps.getSwap(record.swapId, owner.authority.keyPair)
            val swap = found.getOrNull()
            if (swap == null) {
                val error = found.exceptionOrNull()
                if (error is GetSwapError.NotFound) continue
                return Result.failure(error ?: IllegalStateException("getSwap returned nothing"))
            }
            when (swap.state) {
                SwapState.FINALIZED, SwapState.FAILED, SwapState.CANCELLING, SwapState.CANCELLED -> Unit
                else -> transactions.pollSwapForState(record.swapId, owner, SwapState.FINALIZED)
                    .onFailure { return Result.failure(it) }
            }
            store.remove(requestId, record.swapId)
        }
        return Result.success(Unit)
    }

    private suspend fun runLeg(
        requestId: ID,
        leg: FundingLeg,
        paying: Token,
        balances: List<FundingBalance>,
        rate: Rate,
        owner: AccountCluster,
    ): Result<Unit> {
        val from = tokenCoordinator.cachedToken(Mint(leg.source))
            ?: return Result.failure(CollectionFundingError.UnknownCurrency())
        val record = store.legs(requestId).firstOrNull { it.from == from.address && it.to == paying.address }
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
        transactions.pollSwapForState(record.swapId, owner, SwapState.FINALIZED)
            .onFailure { return Result.failure(it) }
        store.remove(requestId, record.swapId)
        return Result.success(Unit)
    }

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

    /** The plan named a currency the wallet no longer knows. */
    class UnknownCurrency : CollectionFundingError("a funding currency is no longer known")
}
