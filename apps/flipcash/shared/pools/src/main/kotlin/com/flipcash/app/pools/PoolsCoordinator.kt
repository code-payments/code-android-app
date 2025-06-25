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
import com.flipcash.app.persistence.BetOutcomeConverter
import com.flipcash.app.persistence.PoolResolutionConverter
import com.flipcash.app.persistence.sources.PoolDataSource
import com.flipcash.app.persistence.sources.mapper.pools.NetworkPoolToDomainMapper
import com.flipcash.app.persistence.sources.mapper.pools.PoolBetEntityToPoolBetMapper
import com.flipcash.app.persistence.sources.mapper.pools.PoolEntityToPoolMapper
import com.flipcash.app.persistence.sources.mediator.PoolRemoteMediator
import com.flipcash.services.controllers.PoolController
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.datetime.Clock
import javax.inject.Inject

class PoolsCoordinator @Inject constructor(
    private val controller: PoolController,
    private val dataSource: PoolDataSource,
    private val entityToDomainMapper: PoolEntityToPoolMapper,
    private val betMapper: PoolBetEntityToPoolBetMapper,
    private val networkToDomainMapper: NetworkPoolToDomainMapper,
    private val domainToNetworkMapper: PoolToMetadataMapper,
    private val userManager: UserManager,
) {
    private val pagingConfig = PagingConfig(pageSize = 20)

    @OptIn(ExperimentalPagingApi::class)
    private val _pools: Flow<PagingData<PoolWithBets>> = userManager.state
        .filter { it.authState.canAccessAuthenticatedApis }
        .flatMapLatest {
            Pager(
                config = pagingConfig,
                remoteMediator = PoolRemoteMediator(controller, dataSource)
            ) {
                dataSource.observe()
            }.flow.map { page ->
                page.map { entity ->
                    PoolWithBets(
                        pool = entityToDomainMapper.map(entity.pool),
                        rendezvous = null,
                        isHost = userManager.accountId == entity.pool.creator,
                        bets = entity.bets.map { betMapper.map(it) }
                    )
                }
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    val pools: Flow<PagingData<PoolWithBets>> = userManager.state
        .mapNotNull { it.authState }
        .filter { it.canAccessAuthenticatedApis }
        .flatMapLatest { _pools }

    suspend fun createPool(
        name: String,
        buyIn: Fiat,
    ): Result<ID> {

        val (metadata, rendezvous) = controller.createPool(
            name = name,
            buyIn = buyIn,
        ).getOrElse { return Result.failure(it) }

        dataSource.upsert(metadata)
        dataSource.upsertRendezvous(rendezvous)

        return Result.success(metadata.id)
    }

    suspend fun getPool(id: ID): Result<PoolWithBets> {
        val networkPool = controller.getPool(id)
            .getOrElse { return Result.failure(it) }

        val isHost = networkPool.metadata.creator == userManager.accountId

        if (isHost) {
            // if we are the host, we can query for the rendezvous
            val rendezvous = userManager.poolAccounts.find { it.address.bytes == networkPool.metadata.fundingDestination.bytes }
                ?.let { account ->
                    val index = account.index.toLong()
                    val path = DerivePath.getPoolRendezvous(index)
                    userManager.mnemnonic?.getSolanaKeyPair(path)
                }

            if (rendezvous != null) {
                dataSource.upsertRendezvous(rendezvous, networkPool.rendezvousSignature)
            }
        }


        // store the pool if we are the host or if we have bet already
        if (isHost || networkPool.bets.any { it.metadata.userId == userManager.accountId }) {
            dataSource.upsert(listOf(networkPool))
        }

        return runCatching {
            dataSource.getById(id)!!
        }.recoverCatching { networkToDomainMapper.map(networkPool to null) }
    }

    suspend fun getPool(rendezvous: KeyPair): Result<PoolWithBets> {
        val networkPool = controller.getPool(rendezvous)
            .getOrElse { return Result.failure(it) }

        val (_, _, isHost, bets) = networkToDomainMapper.map(networkPool to rendezvous)

        // store the pool if we are the host or if we have bet already
        if (isHost || bets.any { it.userId == userManager.accountId }) {
            dataSource.upsert(listOf(networkPool))
            dataSource.upsertRendezvous(rendezvous, networkPool.rendezvousSignature)
        }

        return runCatching {
            dataSource.getById(rendezvous.publicKeyBytes.toList())!!
        }.recoverCatching { networkToDomainMapper.map(networkPool to rendezvous) }
    }

    fun observePool(id: ID): Flow<PoolWithBets?> {
        return dataSource.observe(id)
    }

    suspend fun closePool(
        pool: Pool,
        rendezvous: KeyPair,
    ): Result<Unit> {
        val metadata = domainToNetworkMapper.map(pool)
        dataSource.closePool(pool.id)
        return controller.closePool(metadata, rendezvous)
            .onFailure {
                dataSource.reopenPool(pool.id)
            }
    }

    suspend fun resolvePool(
        pool: Pool,
        resolution: PoolResolution.DecisionMade,
        rendezvous: KeyPair
    ): Result<Unit> {
        val metadata = domainToNetworkMapper.map(pool)
        dataSource.resolvePool(pool.id, resolution)
        return controller.resolvePool(
            pool = metadata,
            rendezvous = rendezvous,
            resolution = PoolResolutionConverter.toPoolResolution(resolution as PoolResolution),
        ).onFailure {
            dataSource.unresolvePool(pool.id)
        }
    }

    suspend fun placeBet(
        poolId: ID,
        rendezvous: KeyPair,
        outcome: PoolBetOutcome
    ): Result<Unit> {
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
        dataSource.upsertBet(poolId, metadata, false)

        return controller.placeBet(
            poolId = poolId,
            rendezvous = rendezvous,
            metadata = metadata,
        ).onFailure {
            dataSource.removeBet(poolId, metadata.id)
        }.map { Unit }
    }

    suspend fun fetchSinceLatest(count: Int = 20): Result<Unit> {
        val latest = dataSource.getMostRecent()
        return controller.getPagedPools(
            queryOptions = QueryOptions(
                limit = count,
                token = latest?.pool?.id,
                descending = latest == null,
            )
        ).onSuccess {
            dataSource.upsert(it) }.map { Unit }
    }
}