package com.flipcash.shared.payments.request

import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.model.core.ID
import com.getcode.solana.keys.Mint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** [CollectionFundingStore] in memory, to read what funding kept. */
internal class InMemoryCollectionFundingStore : CollectionFundingStore {
    val kept = mutableMapOf<ID, List<FundingLegRecord>>()
    val topUps = mutableMapOf<ID, PendingTopUpRecord>()
    private val lastPaid = MutableStateFlow<Mint?>(null)

    override suspend fun legs(requestId: ID): List<FundingLegRecord> = kept[requestId].orEmpty()

    override suspend fun put(requestId: ID, leg: FundingLegRecord) {
        kept[requestId] = legs(requestId) + leg
    }

    override suspend fun remove(requestId: ID, swapId: SwapId) {
        kept[requestId] = legs(requestId).filterNot { it.swapId == swapId }
    }

    override suspend fun clear(requestId: ID) {
        kept.remove(requestId)
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
