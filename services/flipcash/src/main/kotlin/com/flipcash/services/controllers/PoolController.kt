package com.flipcash.services.controllers

import com.flipcash.services.internal.model.pools.Resolution
import com.flipcash.services.models.Pool
import com.flipcash.services.models.PoolMetadata
import com.flipcash.services.repository.PoolRepository
import com.flipcash.services.user.UserManager
import com.getcode.opencode.controllers.AccountController as OpenCodeAccountController
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.utils.mapResult
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.Signature
import com.getcode.utils.base58
import javax.inject.Inject

class PoolController @Inject constructor(
    private val repository: PoolRepository,
    private val userManager: UserManager,
    private val accountController: OpenCodeAccountController,
) {
    suspend fun createPool(
        name: String,
        buyIn: Fiat,
    ): Result<Pool> {
        val owner = userManager.accountCluster
            ?: return Result.failure(Throwable("No account cluster in UserManager"))
        val userId = userManager.accountId
            ?: return Result.failure(Throwable("No account ID in UserManager"))

        val poolAccount = accountController.createPoolAccount(owner, userManager.nextPoolIndex)
            .getOrElse { return Result.failure(it) }

        return repository.createPool(
            owner = owner.authority.keyPair,
            name = name,
            userId = userId,
            buyIn = buyIn,
            fundingDestination = PublicKey(poolAccount),
        ).mapResult { getPool(it.id) }
            .onSuccess {
                println("Pool created: ${it.metadata.id.base58}")
            }
    }

    suspend fun getPool(poolId: ID) = repository.getPool(poolId)

    suspend fun declareOutcome(
        pool: PoolMetadata,
        rendezvous: Signature,
        resolution: Boolean,
    ): Result<Unit> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.declareOutcome(
            owner = owner,
            poolId = pool.id,
            resolution = resolution,
            poolRendezvous = rendezvous,
        )
    }

    suspend fun placeBet(
        pool: PoolMetadata,
        rendezvous: Signature,
        choice: Boolean,
    ): Result<Unit> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))
        val userId = userManager.accountId
            ?: return Result.failure(Throwable("No account ID in UserManager"))


        return repository.placeBet(
            owner = owner,
            userId = userId,
            poolId = pool.id,
            choice = choice,
            payoutDestination = pool.fundingDestination,
            rendezvous = rendezvous,
        )
    }
}