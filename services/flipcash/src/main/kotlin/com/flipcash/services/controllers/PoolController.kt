package com.flipcash.services.controllers

import com.flipcash.services.internal.model.pools.PoolRequest
import com.flipcash.services.models.NetworkPool
import com.flipcash.services.models.NetworkPoolBetOutcome
import com.flipcash.services.models.NetworkPoolResolution
import com.flipcash.services.models.PoolBetMetadata
import com.flipcash.services.models.PoolMetadata
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.repository.PoolRepository
import com.flipcash.services.user.UserManager
import com.getcode.ed25519.Ed25519
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.PublicKey
import javax.inject.Inject
import com.getcode.opencode.controllers.AccountController as OpenCodeAccountController

class PoolController @Inject constructor(
    private val repository: PoolRepository,
    private val userManager: UserManager,
    private val accountController: OpenCodeAccountController,
) {
    suspend fun createPool(
        name: String,
        buyIn: Fiat,
    ): Result<Pair<PoolMetadata, KeyPair>> {
        val owner = userManager.accountCluster
            ?: return Result.failure(Throwable("No account cluster in UserManager"))
        val userId = userManager.accountId
            ?: return Result.failure(Throwable("No account ID in UserManager"))

        val poolAccount = accountController.createPoolAccount(owner, userManager.nextPoolIndex)
            .getOrElse { return Result.failure(it) }

        val rendezvous = Ed25519.createKeyPair()
        return repository.createPool(
            owner = owner.authority.keyPair,
            name = name,
            userId = userId,
            buyIn = buyIn,
            fundingDestination = poolAccount.cluster.vaultPublicKey,
            rendezvous = rendezvous,
        ).map {
            it to rendezvous
        }
    }

    suspend fun getPool(poolId: ID) = repository.getPool(poolId)

    suspend fun getPagedPools(queryOptions: QueryOptions): Result<List<NetworkPool>> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.getPagedPools(owner, queryOptions)
    }

    suspend fun closePool(
        pool: PoolMetadata,
    ): Result<Unit> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.closePool(
            owner = owner,
            pool = pool,
        )
    }

    suspend fun resolvePool(
        pool: PoolMetadata,
        resolution: NetworkPoolResolution,
    ): Result<Unit> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        val rendezvous = Ed25519.createKeyPair()

        return repository.declareOutcome(
            owner = owner,
            pool = pool,
            resolution = resolution,
            rendezvous = rendezvous,
        )
    }

    suspend fun placeBet(
        poolId: ID,
        rendezvous: KeyPair,
        choice: NetworkPoolBetOutcome,
    ): Result<PoolBetMetadata> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))
        val userId = userManager.accountId
            ?: return Result.failure(Throwable("No account ID in UserManager"))

        val vault = userManager.accountCluster?.vaultPublicKey
            ?: return Result.failure(Throwable("No vault public key in UserManager"))

        return repository.placeBet(
            owner = owner,
            userId = userId,
            poolId = poolId,
            choice = choice,
            payoutDestination = vault,
            rendezvous = rendezvous,
        )
    }
}