package com.flipcash.services.controllers

import com.flipcash.services.internal.network.extensions.toProto
import com.flipcash.services.models.NetworkPool
import com.flipcash.services.models.NetworkPoolBet
import com.flipcash.services.models.NetworkPoolBetOutcome
import com.flipcash.services.models.NetworkPoolResolution
import com.flipcash.services.models.PoolBetMetadata
import com.flipcash.services.models.PoolMetadata
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.repository.PoolRepository
import com.flipcash.services.user.UserManager
import com.flipcash.services.validators.NetworkPoolValidator
import com.getcode.crypt.DerivePath
import com.getcode.ed25519.Ed25519
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.Signature
import com.getcode.utils.CodeServerError
import kotlinx.datetime.Instant
import javax.inject.Inject
import com.getcode.opencode.controllers.AccountController as OpenCodeAccountController

class PoolController @Inject constructor(
    private val repository: PoolRepository,
    private val userManager: UserManager,
    private val accountController: OpenCodeAccountController,
    private val poolValidator: NetworkPoolValidator,
) {
    suspend fun createPool(
        name: String,
        buyIn: Fiat,
    ): Result<PoolMetadata> {
        val owner = userManager.accountCluster
            ?: return Result.failure(Throwable("No account cluster in UserManager"))
        val userId = userManager.accountId
            ?: return Result.failure(Throwable("No account ID in UserManager"))

        val mnemonic = userManager.mnemnonic
            ?: return Result.failure(Throwable("No mnemonic in UserManager"))

        val poolIndex = userManager.nextPoolIndex
        val poolAccount = accountController.createPoolAccount(owner, poolIndex, mnemonic)
            .getOrElse { return Result.failure(it) }

        return repository.createPool(
            owner = owner.authority.keyPair,
            name = name,
            userId = userId,
            buyIn = buyIn,
            fundingDestination = poolAccount.cluster.vaultPublicKey,
            rendezvous = poolAccount.rendezvous,
        )
    }

    suspend fun getPool(rendezvous: KeyPair): Result<NetworkPool> {
        // for now include all bets always
        return repository.getPool(rendezvous.publicKeyBytes.toList(), false)
    }

    suspend fun getPool(id: ID): Result<NetworkPool> {
        // for now include all bets always
        return repository.getPool(id, false)
    }

    suspend fun getBetsForPool(id: ID): Result<List<NetworkPoolBet>> {
        return repository.getPool(id, excludeBets = false)
            .map { it.bets }
    }

    suspend fun getPagedPools(queryOptions: QueryOptions): Result<List<NetworkPool>> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.getPagedPools(owner, queryOptions)
    }

    suspend fun closePool(
        pool: PoolMetadata,
        rendezvous: KeyPair,
    ): Result<Instant> {
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

class TamperedPoolException: CodeServerError("Pool has been tampered with")