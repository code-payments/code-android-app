package com.flipcash.app.pools

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.flipcash.app.core.pools.Pool
import com.flipcash.app.core.pools.PoolBetOutcome
import com.flipcash.app.core.pools.PoolResolution
import com.flipcash.app.core.pools.PoolWithBets
import com.flipcash.app.core.pools.PoolWithHostStatus
import com.flipcash.app.persistence.converters.BetOutcomeConverter
import com.flipcash.app.persistence.converters.PoolResolutionConverter
import com.flipcash.app.persistence.sources.PoolDataSource
import com.flipcash.app.persistence.sources.mapper.pools.NetworkPoolToDomainMapper
import com.flipcash.app.persistence.sources.mapper.pools.PoolEntityToPoolMapper
import com.flipcash.app.persistence.sources.mediator.PoolRemoteMediator
import com.flipcash.services.controllers.PoolController
import com.flipcash.services.extensions.derivePoolBetId
import com.flipcash.services.models.PoolBetMetadata
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.user.UserManager
import com.getcode.crypt.DerivePath
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.utils.generate
import com.getcode.solana.keys.PublicKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import javax.inject.Inject

class PoolsCoordinator @Inject constructor(
    private val controller: PoolController,
    private val dataSource: PoolDataSource,
    private val entityToDomainMapper: PoolEntityToPoolMapper,
    private val networkToDomainMapper: NetworkPoolToDomainMapper,
    private val domainToNetworkMapper: PoolToMetadataMapper,
    private val userManager: UserManager,
) {
    private val pagingConfig = PagingConfig(pageSize = 20)

    @OptIn(ExperimentalPagingApi::class)
    private val _pools: Flow<PagingData<PoolWithHostStatus>> = userManager.state
        .filter { it.authState.canAccessAuthenticatedApis }
        .flatMapLatest {
            Pager(
                config = pagingConfig,
                remoteMediator = PoolRemoteMediator(controller, dataSource)
            ) {
                dataSource.observe()
            }.flow.map { page ->
                page.map { entity ->
                    val pool = entityToDomainMapper.map(entity)
                    val isHost = userManager.accountId == entity.creator
                    PoolWithHostStatus(pool, isHost)
                }
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    val pools: Flow<PagingData<PoolWithHostStatus>> = userManager.state
        .mapNotNull { it.authState }
        .filter { it.canAccessAuthenticatedApis }
        .flatMapLatest { _pools }

    suspend fun updatePools() = coroutineScope {
        val pools = dataSource.get()
        pools.map { (pool, _, _) ->
            async { getPool(pool.id) }
        }.forEach { it.await() }
    }

    suspend fun createPool(
        name: String,
        buyIn: Fiat,
    ): Result<ID> {
        val metadata = controller.createPool(
            name = name,
            buyIn = buyIn,
        ).getOrElse { return Result.failure(it) }

        return Result.success(metadata.id)
    }

    suspend fun getPool(id: ID): Result<PoolWithBets> {
        val networkPool = controller.getPool(id)
            .getOrElse { return Result.failure(it) }

        val isHost = networkPool.metadata.creator == userManager.accountId

        // store the pool if we are the host or if we have bet already
        if (isHost || networkPool.bets.find { it.metadata.userId == userManager.accountId }?.hasIntentBeenSubmitted == true) {
            dataSource.upsert(listOf(networkPool))
        }

        return runCatching {
            dataSource.getById(id)!!
        }.recoverCatching {
            networkToDomainMapper.map(networkPool)
        }
    }

    suspend fun getPool(rendezvous: KeyPair): Result<PoolWithBets> {
        val networkPool = controller.getPool(rendezvous)
            .getOrElse { return Result.failure(it) }

        val (_, isHost, bets) = networkToDomainMapper.map(networkPool)

        // store the pool if we are the host or if we have bet already (and paid for it)
        if (isHost || bets.find { it.userId == userManager.accountId }?.hasPaidForBet == true) {
            dataSource.upsert(listOf(networkPool))
        }

        return runCatching {
            dataSource.getById(rendezvous.publicKeyBytes.toList())!!
        }.recoverCatching { networkToDomainMapper.map(networkPool) }
    }

    fun observePool(id: ID): Flow<PoolWithBets?> {
        return dataSource.observe(id)
    }

    suspend fun closePool(
        pool: Pool,
        rendezvous: KeyPair,
    ): Result<Instant> {
        val metadata = domainToNetworkMapper.map(pool)
        return controller.closePool(metadata, rendezvous)
            .onSuccess { closedAt ->
                dataSource.closePool(pool.id, closedAt)
            }
    }

    suspend fun resolvePool(
        pool: Pool,
        resolution: PoolResolution.DecisionMade,
        rendezvous: KeyPair
    ): Result<Unit> {
        val metadata = domainToNetworkMapper.map(pool)
        return controller.resolvePool(
            pool = metadata,
            rendezvous = rendezvous,
            resolution = PoolResolutionConverter.toPoolResolution(resolution as PoolResolution),
        ).onSuccess {
            dataSource.resolvePool(pool.id, resolution)
        }
    }

    suspend fun placeBet(
        poolId: ID,
        rendezvous: KeyPair,
        outcome: PoolBetOutcome
    ): Result<ID> {
        val userId = userManager.accountId
            ?: return Result.failure(Throwable("No account ID in UserManager"))

        val vault = userManager.accountCluster?.vaultPublicKey
            ?: return Result.failure(Throwable("No vault public key in UserManager"))

        val metadata = PoolBetMetadata(
            id = PublicKey.generate().bytes,
            userId = userId,
            payoutDestination = vault,
            selectedOutcome = BetOutcomeConverter.toBetOutcome(outcome),
            timestamp = Clock.System.now(),
        )

        return controller.placeBet(
            poolId = poolId,
            rendezvous = rendezvous,
            metadata = metadata,
        ).onSuccess {
            dataSource.upsertBet(poolId, it, false)
        }.map { it.id }
    }

    suspend fun onBetPaidForInPool(poolId: ID): Result<Unit> {
        val userId = userManager.accountId
            ?: return Result.failure(Throwable("No account ID in UserManager"))

        return runCatching { dataSource.paidBet(derivePoolBetId(poolId, userId).bytes) }
    }

    suspend fun fetchSinceLatest(count: Int = 20): Result<Unit> {
        val latest = dataSource.getMostRecent()
        return controller.getPagedPools(
            queryOptions = QueryOptions(
                limit = count,
                token = latest?.pool?.id?.takeIf { it.isNotEmpty() },
                descending = latest == null,
            )
        ).onSuccess {
            dataSource.upsert(it)
        }.map { Unit }
    }
}