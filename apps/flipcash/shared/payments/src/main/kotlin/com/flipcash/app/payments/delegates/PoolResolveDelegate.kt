package com.flipcash.app.payments.delegates

import com.flipcash.app.core.pools.Pool
import com.flipcash.app.core.pools.PoolBet
import com.flipcash.app.core.pools.PoolResolution
import com.getcode.ed25519.Ed25519

interface PoolResolveDelegate {
    suspend fun resolvePool(
        pool: Pool,
        bets: List<PoolBet>,
        rendezvous: Ed25519.KeyPair,
        resolution: PoolResolution.DecisionMade,
        onEvent: suspend (DelegateEvent) -> Unit,
        onError: suspend (Throwable) -> Unit,
    )
}