package com.flipcash.app.payments.delegates

import com.flipcash.app.core.pools.Pool
import com.flipcash.app.core.pools.PoolBet
import com.flipcash.app.core.pools.PoolResolution
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.model.core.ID

interface PoolResolveDelegate {
    suspend fun resolvePool(
        pool: Pool,
        bets: List<PoolBet>,
        rendezvous: Ed25519.KeyPair,
        resolution: PoolResolution.DecisionMade,
        onSuccess: suspend (ID) -> Unit,
        onError: suspend (Throwable) -> Unit,
    )
}