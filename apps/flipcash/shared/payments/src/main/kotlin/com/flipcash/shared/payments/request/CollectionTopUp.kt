package com.flipcash.shared.payments.request

import com.flipcash.app.tokens.TokenCoordinator
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.core.ID
import com.getcode.solana.keys.Mint
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** An order placed for a top-up, before Google Pay opens on it. */
data class TopUpOrder(val orderId: String, val paymentLink: String)

/** How Google Pay ended for a top-up's order. */
enum class TopUpPayment {
    /** Coinbase reported the payment complete. The Dollars land later. */
    Succeeded,

    /** Google Pay failed or closed before the payment was authorized: nothing was charged. */
    NotCharged,

    /** The payment was authorized and then the webview failed or closed: the order may still settle. */
    Unknown,
}

/** Whether an order's asset reached the account on-chain. */
enum class TopUpDelivery { Delivered, Failed, TimedOut }

/** The rail a collection's top-up runs on: Coinbase guest checkout with Google Pay on Android. */
interface CollectionTopUpRail {
    /**
     * Checks the purchase can run (Google Pay, the WebView, a verified phone and email) and orders
     * exactly [usdCents] to land, with the fee on top. A failure ordered nothing.
     */
    suspend fun order(usdCents: Long, checkGates: Boolean): Result<TopUpOrder>

    /** Opens Google Pay on [order] and suspends until it ends. */
    suspend fun pay(order: TopUpOrder): TopUpPayment

    /** Waits for [orderId]'s on-chain send, or for the order to fail. */
    suspend fun awaitDelivered(orderId: String): TopUpDelivery

    /** Turns delivered USDC into Dollars. It runs in the background; the Dollars balance shows the result. */
    fun sweep(owner: AccountCluster)
}

/** Why a top-up couldn't be ordered. Nothing was ordered or kept. */
sealed interface TopUpBlock {
    data object GooglePayNotSupported : TopUpBlock

    data object GooglePayNoPaymentMethod : TopUpBlock

    /** The system webview is a pre-release [channel]; the user may continue anyway. */
    data class WebViewWarning(val channel: String) : TopUpBlock

    /** Coinbase needs a verified [phone] or [email] first. */
    data class Verification(val phone: Boolean, val email: Boolean) : TopUpBlock

    data class OrderFailed(val cause: Throwable) : TopUpBlock
}

/** A [CollectionTopUpRail] failure that says why the order wasn't placed. */
class CollectionTopUpBlocked(val block: TopUpBlock) : Exception(block.toString())

/** Where a collection's top-up stands once [CollectionTopUp] returns. */
sealed interface TopUpResult {
    /** The top-up's Dollars are in the balance and its record is gone: replan and pay. */
    data object Landed : TopUpResult

    /** Google Pay ended before it charged anything. Nothing is kept, so the short state shows again. */
    data object NotCharged : TopUpResult

    /** The order failed, so nothing will land. Nothing is kept. */
    data object Failed : TopUpResult

    /** The top-up's outcome isn't known yet. It stays kept, so a retry waits on it again. */
    data object StillPending : TopUpResult

    /** The purchase couldn't start ([error] says why), and nothing was ordered. */
    data class Blocked(val block: TopUpBlock) : TopUpResult
}

/**
 * One lock per payment request, shared by every screen that can pay it (chat and Request
 * details each own a pay flow), so a second tap waits on the first instead of ordering again.
 */
@Singleton
class CollectionTopUpLocks @Inject constructor() {
    private val guard = Mutex()
    private val locks = mutableMapOf<ID, Mutex>()

    suspend fun <T> withLock(requestId: ID, block: suspend () -> T): T {
        val lock = guard.withLock { locks.getOrPut(requestId) { Mutex() } }
        return lock.withLock { block() }
    }
}

/**
 * Tops up Dollars for a collection payment the balances can't cover (group-collections spec,
 * "Adding money and recovery"). The order is kept from before Google Pay opens until its Dollars
 * are seen or it is known to have failed, so a retry, a relaunch or a second tap waits on it and
 * never orders again. Run [topUp] and [resume], and the payment after them, inside [exclusive].
 */
class CollectionTopUp @Inject constructor(
    private val rail: CollectionTopUpRail,
    private val store: CollectionFundingStore,
    private val tokenCoordinator: TokenCoordinator,
    private val locks: CollectionTopUpLocks,
) {

    /** How long to wait between balance fetches for swept Dollars to show; tests shorten it. */
    @androidx.annotation.VisibleForTesting
    internal var settleInterval: Duration = 2.seconds

    /** Runs [block] with [requestId]'s top-up to itself: ordering, waiting and the payment after. */
    suspend fun <T> exclusive(requestId: ID, block: suspend () -> T): T = locks.withLock(requestId, block)

    /** The top-up kept for [requestId], its outcome not yet known, or null when none is kept. */
    suspend fun kept(requestId: ID): PendingTopUpRecord? = store.pendingTopUp(requestId)

    /**
     * Orders [usdCents] for [requestId] and opens Google Pay on it, then waits for the Dollars. The
     * order is kept with [payingCurrency], the currency the payment is to be made in once they land
     * (spec, "Adding money and recovery" item 1). A top-up already kept for [requestId] is waited on
     * instead, and nothing new is ordered. [onCharged] runs once Google Pay has closed on a payment
     * that may settle, before the wait. [checkGates] false skips the Google Pay checks, after the
     * user chose to continue anyway.
     */
    suspend fun topUp(
        requestId: ID,
        usdCents: Long,
        payingCurrency: Mint,
        owner: AccountCluster,
        checkGates: Boolean = true,
        onCharged: () -> Unit = {},
    ): TopUpResult {
        store.pendingTopUp(requestId)?.let { return settle(requestId, it, owner) }
        val baseline = dollarsMicros()
        val order = rail.order(usdCents, checkGates).getOrElse { cause ->
            return TopUpResult.Blocked((cause as? CollectionTopUpBlocked)?.block ?: TopUpBlock.OrderFailed(cause))
        }
        val record = PendingTopUpRecord(order.orderId, usdCents, baseline, payingCurrency)
        store.putPendingTopUp(requestId, record)
        return when (rail.pay(order)) {
            TopUpPayment.NotCharged -> {
                store.clearPendingTopUp(requestId)
                TopUpResult.NotCharged
            }
            TopUpPayment.Succeeded, TopUpPayment.Unknown -> {
                onCharged()
                settle(requestId, record, owner)
            }
        }
    }

    /** Waits on the top-up kept for [requestId], or returns null when none is kept. */
    suspend fun resume(requestId: ID, owner: AccountCluster): TopUpResult? =
        store.pendingTopUp(requestId)?.let { settle(requestId, it, owner) }

    /**
     * The Dollars are seen when the balance reaches the baseline plus 98% of the order (as iOS
     * checks), or, once the order is delivered and swept, when it rises above the baseline at
     * all: a short delivery still clears, and the replan after it asks before any further top-up.
     */
    private suspend fun settle(requestId: ID, record: PendingTopUpRecord, owner: AccountCluster): TopUpResult {
        val landedMicros = record.usdCents * MICROS_PER_CENT * LANDED_PERCENT / 100
        if (dollarsMicros() >= record.baselineDollarsMicros + landedMicros) {
            store.clearPendingTopUp(requestId)
            return TopUpResult.Landed
        }
        return when (rail.awaitDelivered(record.orderId)) {
            TopUpDelivery.Failed -> {
                store.clearPendingTopUp(requestId)
                TopUpResult.Failed
            }
            TopUpDelivery.TimedOut -> TopUpResult.StillPending
            TopUpDelivery.Delivered -> {
                rail.sweep(owner)
                if (awaitDollarsAbove(record.baselineDollarsMicros)) {
                    store.clearPendingTopUp(requestId)
                    TopUpResult.Landed
                } else {
                    TopUpResult.StillPending
                }
            }
        }
    }

    private suspend fun awaitDollarsAbove(baselineMicros: Long): Boolean {
        repeat(SWEEP_CHECKS) { check ->
            if (check > 0) delay(settleInterval)
            if (dollarsMicros() > baselineMicros) return true
        }
        return false
    }

    private suspend fun dollarsMicros(): Long {
        tokenCoordinator.update()
        return tokenCoordinator.currentBalance(Mint.usdf).quarks
    }

    private companion object {
        const val MICROS_PER_CENT = 10_000L
        const val LANDED_PERCENT = 98L

        /** A sweep usually lands in seconds; this waits about a minute before calling it pending. */
        const val SWEEP_CHECKS = 30
    }
}
