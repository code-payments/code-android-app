package com.flipcash.services.controllers

import com.flipcash.services.models.NetworkPool
import com.flipcash.services.models.NetworkPoolBetOutcome
import com.flipcash.services.models.NetworkPoolResolution
import com.flipcash.services.models.PoolBetMetadata
import com.flipcash.services.models.PoolMetadata
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.repository.PoolRepository
import com.flipcash.services.user.UserManager
import com.getcode.crypt.DerivePath
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Fiat
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

        val path = DerivePath.getPoolRendezvous(userManager.nextPoolIndex)
        val rendezvous = userManager.mnemnonic?.getSolanaKeyPair(path)
            ?: return Result.failure(Throwable("No mnemonic in UserManager"))
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

    suspend fun getPool(rendezvous: KeyPair) = repository.getPool(rendezvous.publicKeyBytes.toList())
    suspend fun getPool(id: ID) = repository.getPool(id)

    suspend fun getPagedPools(queryOptions: QueryOptions): Result<List<NetworkPool>> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.getPagedPools(owner, queryOptions)
    }

    suspend fun closePool(
        pool: PoolMetadata,
        rendezvous: KeyPair,
    ): Result<Unit> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.closePool(
            owner = owner,
            pool = pool,
            poolRendezvous = rendezvous,
        )
    }

    suspend fun resolvePool(
        pool: PoolMetadata,
        resolution: NetworkPoolResolution,
        rendezvous: KeyPair,
    ): Result<Unit> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.declareOutcome(
            owner = owner,
            pool = pool,
            resolution = resolution,
            poolRendezvous = rendezvous,
        )
    }

    suspend fun placeBet(
        poolId: ID,
        rendezvous: KeyPair,
        metadata: PoolBetMetadata,
    ): Result<PoolBetMetadata> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.placeBet(
            owner = owner,
            poolId = poolId,
            poolRendezvous = rendezvous,
            metadata = metadata,
        )
    }
}