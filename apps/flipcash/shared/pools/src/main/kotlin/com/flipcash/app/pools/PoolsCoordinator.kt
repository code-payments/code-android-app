package com.flipcash.app.pools

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.flipcash.app.core.cache.CachePolicy
import com.flipcash.app.core.cache.CachePolicyHandler
import com.flipcash.app.core.cache.CacheEntry
import com.flipcash.app.core.pools.Pool
import com.flipcash.app.core.pools.PoolBetOutcome
import com.flipcash.app.core.pools.PoolResolution
import com.flipcash.app.core.pools.PoolWithBets
import com.flipcash.app.core.pools.PoolWithHostStatus
import com.flipcash.app.persistence.converters.BetOutcomeConverter
import com.flipcash.app.persistence.converters.PoolResolutionConverter
import com.flipcash.app.persistence.sources.PoolDataSource
import com.flipcash.app.persistence.sources.mapper.pools.NetworkPoolMapperParameters
import com.flipcash.app.persistence.sources.mapper.pools.NetworkPoolToDomainMapper
import com.flipcash.app.persistence.sources.mapper.pools.PoolEntityToPoolMapper
import com.flipcash.app.persistence.sources.mediator.PoolRemoteMediator
import com.flipcash.services.controllers.PoolController
import com.flipcash.services.extensions.derivePoolBetId
import com.flipcash.services.models.NetworkPool
import com.flipcash.services.models.PoolBetMetadata
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.user.UserManager
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.controllers.AccountController
import com.getcode.opencode.model.accounts.AccountFilter
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Fiat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PoolsCoordinator @Inject constructor(
    private val accountController: AccountController,
    private val controller: PoolController,
    private val dataSource: PoolDataSource,
    private val entityToDomainMapper: PoolEntityToPoolMapper,
    private val networkToDomainMapper: NetworkPoolToDomainMapper,
    private val userManager: UserManager,
) {
    private val pagingConfig = PagingConfig(pageSize = 20)

    private val _poolOpen = MutableStateFlow<ID?>(null)
    val openPool: StateFlow<ID?>
        get() = _poolOpen.asStateFlow()

    fun onPoolOpened(id: ID) {
        _poolOpen.value = id
    }

    fun onPoolClosed() {
        _poolOpen.value = null
    }

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
        pools.map { (pool, _, _, _) ->
            async { updatePool(pool.id) }
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

    suspend fun getPool(
        id: ID,
        cachePolicy: CachePolicy = CachePolicy.CacheFirst
    ): Result<CacheEntry<PoolWithBets>> {
        val handler = CachePolicyHandler<PoolWithBets, NetworkPool>()
        return handler.execute(
            cachePolicy = cachePolicy,
            cacheLookup = { dataSource.getById(id) },
            persistNetworkData = { networkPool ->
                val isHost = networkPool.metadata.creator == userManager.accountId
                if (isHost) {
                    val rendezvous = userManager.poolAccountAt(networkPool.derivationIndex).rendezvous
                    dataSource.persistRendezvous(networkPool.metadata.id, rendezvous)
                }

                dataSource.upsert(listOf(networkPool))
            },
            networkRequest = { controller.getPool(id) },
            domainMapper = { networkPool ->
                networkToDomainMapper.map(NetworkPoolMapperParameters(networkPool))
            }
        )
    }

    suspend fun updatePool(id: ID) {
        val pool = dataSource.getById(id)
        val hasDecision = pool?.pool?.resolution is PoolResolution.DecisionMade
        if (!hasDecision) {
            getPool(id, CachePolicy.NetworkOnly)
        }
    }

    suspend fun getPool(rendezvous: KeyPair): Result<PoolWithBets> {
        val networkPool = controller.getPool(rendezvous)
            .getOrElse { return Result.failure(it) }

        val parameters = NetworkPoolMapperParameters(networkPool, rendezvous)
        val mappedPool = networkToDomainMapper.map(parameters)
        val (_, isHost, _, bets, _) = mappedPool

        // store the pool if we are the host or if we have bet already (and paid for it)
        if (isHost || bets.find { it.userId == userManager.accountId }?.hasPaidForBet == true) {
            dataSource.upsert(listOf(networkPool))
        }
        dataSource.persistRendezvous(networkPool.metadata.id, rendezvous)

        return runCatching {
            dataSource.getById(rendezvous.publicKeyBytes.toList())!!
        }.recoverCatching {
            mappedPool
        }
    }

    suspend fun isPoolDistributed(pool: Pool): Result<Boolean> {
        val owner = userManager.accountCluster
            ?: return Result.failure(Throwable("No account cluster"))
        return accountController.getAccount(
            accountOwner = owner,
            requestingOwner = owner,
            filter = AccountFilter.TokenAddress(pool.fundingDestination)
        ).map {
            it.balance == 0L
        }.onFailure {
            return Result.failure(it)
        }
    }

    fun observePool(id: ID): Flow<PoolWithBets?> {
        return dataSource.observe(id)
    }

    suspend fun closePool(
        pool: Pool,
        rendezvous: KeyPair,
    ): Result<Instant> {
        // ensure we are working with an up-to-date instance of a pool
        return controller.getPool(pool.id)
            .fold(
                onSuccess = {
                    controller.closePool(it.metadata, rendezvous)
                },
                onFailure = {
                    Result.failure(it)
                }
            ).onSuccess { closedAt ->
                dataSource.closePool(pool.id, closedAt)
            }
    }

    suspend fun resolvePool(
        pool: Pool,
        resolution: PoolResolution.DecisionMade,
        rendezvous: KeyPair
    ): Result<Unit> {
        // ensure we are working with an up-to-date instance of a pool
        return controller.getPool(pool.id)
            .fold(
                onSuccess = {
                    controller.resolvePool(
                        pool = it.metadata,
                        rendezvous = rendezvous,
                        resolution = PoolResolutionConverter.toPoolResolution(resolution as PoolResolution),
                    )
                },
                onFailure = { Result.failure(it) }
            ).onSuccess {
                updatePool(pool.id)
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
            id = derivePoolBetId(poolId, userId).bytes,
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
            getPool(poolId, CachePolicy.NetworkOnly) // get and store pool
        }.map { it.id }
    }

    suspend fun onBetPaidForInPool(poolId: ID): Result<Unit> {
        val userId = userManager.accountId
            ?: return Result.failure(Throwable("No account ID in UserManager"))

        dataSource.paidBet(derivePoolBetId(poolId, userId).bytes)
        // TODO: once we have streams setup for pool this can be removed
        updatePool(poolId)

        return Result.success(Unit)
    }

    suspend fun fetchSinceLatest(count: Int = 20): Result<Unit> {
        val latest = dataSource.getMostRecent()
        return controller.getPagedPools(
            queryOptions = QueryOptions(
                limit = count,
                token = latest?.pagingToken?.takeIf { it.isNotEmpty() },
                descending = latest == null,
            )
        ).onSuccess {
            dataSource.upsert(it)
        }.map { Unit }
    }
}