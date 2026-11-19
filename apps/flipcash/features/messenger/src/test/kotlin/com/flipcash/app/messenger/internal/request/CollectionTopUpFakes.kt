package com.flipcash.app.messenger.internal.request

import com.flipcash.shared.payments.request.CollectionFundingStore
import com.flipcash.shared.payments.request.CollectionTopUpRail
import com.flipcash.shared.payments.request.FundingLegRecord
import com.flipcash.shared.payments.request.PendingTopUpRecord
import com.flipcash.shared.payments.request.TopUpDelivery
import com.flipcash.shared.payments.request.TopUpOrder
import com.flipcash.shared.payments.request.TopUpPayment
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.core.ID
import com.getcode.solana.keys.Mint
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** The funding store as the device keeps it: it outlives a flow, as DataStore outlives the process. */
internal class InMemoryCollectionFundingStore : CollectionFundingStore {
    val topUps = mutableMapOf<ID, PendingTopUpRecord>()
    private val legs = mutableMapOf<ID, List<FundingLegRecord>>()
    private val lastPaid = MutableStateFlow<Mint?>(null)

    override suspend fun legs(requestId: ID): List<FundingLegRecord> = legs[requestId].orEmpty()
    override suspend fun put(requestId: ID, leg: FundingLegRecord) {
        legs[requestId] = legs[requestId].orEmpty() + leg
    }
    override suspend fun remove(requestId: ID, swapId: SwapId) {
        legs[requestId] = legs[requestId].orEmpty().filterNot { it.swapId == swapId }
    }
    override suspend fun clear(requestId: ID) {
        legs.remove(requestId)
    }
    override suspend fun pendingTopUp(requestId: ID): PendingTopUpRecord? = topUps[requestId]
    override suspend fun putPendingTopUp(requestId: ID, record: PendingTopUpRecord) {
        topUps[requestId] = record
    }
    override suspend fun clearPendingTopUp(requestId: ID) {
        topUps.remove(requestId)
    }
    override fun lastPaid(): Flow<Mint?> = lastPaid
    override suspend fun setLastPaid(mint: Mint) {
        lastPaid.value = mint
    }
}

/**
 * Coinbase and Google Pay stood in for. It records each order and its checks; a sweep adds the
 * ordered cents to Dollars through [addDollars], or [deliverMicros] when the delivery is short.
 * [googlePay] and [delivery] hold Google Pay open or the order undelivered until completed.
 */
internal class FakeCollectionTopUpRail(private val addDollars: (Long) -> Unit) : CollectionTopUpRail {
    val orders = mutableListOf<Long>()
    val gateChecks = mutableListOf<Boolean>()
    var orderFailure: Throwable? = null
    var payment = TopUpPayment.Succeeded
    var deliveryResult = TopUpDelivery.Delivered
    var deliverMicros: Long? = null
    var googlePay: CompletableDeferred<Unit>? = null
    var delivery: CompletableDeferred<Unit>? = null
    private val cents = mutableMapOf<String, Long>()
    private val undelivered = mutableListOf<String>()

    override suspend fun order(usdCents: Long, checkGates: Boolean): Result<TopUpOrder> {
        gateChecks += checkGates
        orderFailure?.let { return Result.failure(it) }
        orders += usdCents
        val id = "order-${orders.size}"
        cents[id] = usdCents
        return Result.success(TopUpOrder(id, "https://pay"))
    }

    /** The charge is Coinbase's once authorized, so it delivers even if the app dies with Google Pay open. */
    override suspend fun pay(order: TopUpOrder): TopUpPayment {
        if (payment != TopUpPayment.NotCharged) undelivered += order.orderId
        googlePay?.await()
        return payment
    }

    override suspend fun awaitDelivered(orderId: String): TopUpDelivery {
        delivery?.await()
        return deliveryResult
    }

    override fun sweep(owner: AccountCluster) {
        undelivered.forEach { id -> addDollars(deliverMicros ?: (cents.getValue(id) * MICROS_PER_CENT)) }
        undelivered.clear()
    }

    private companion object {
        const val MICROS_PER_CENT = 10_000L
    }
}
