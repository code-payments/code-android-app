package com.flipcash.app.payments.internal

import com.flipcash.app.core.pools.Pool
import com.flipcash.app.core.pools.PoolBet
import com.flipcash.app.core.pools.PoolResolution
import com.flipcash.app.payments.delegates.DelegateEvent
import com.flipcash.app.payments.delegates.PoolResolveDelegate
import com.flipcash.services.user.UserManager
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.controllers.TransactionController
import javax.inject.Inject

class InternalPoolResolveDelegate @Inject constructor(
    private val transactionController: TransactionController,
    private val userManager: UserManager,
): PoolResolveDelegate {
    override suspend fun resolvePool(
        pool: Pool,
        bets: List<PoolBet>,
        rendezvous: Ed25519.KeyPair,
        resolution: PoolResolution.DecisionMade,
        onEvent: suspend (DelegateEvent) -> Unit,
        onError: suspend (Throwable) -> Unit,
    ) {
        // TODO: implement distribution
        onEvent(DelegateEvent.Sent)
    }
}