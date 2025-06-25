package com.flipcash.app.persistence.sources

import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.room.withTransaction
import com.flipcash.app.core.pools.PoolResolution
import com.flipcash.app.core.pools.PoolWithBets
import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.entities.PoolWithBetsEntity
import com.flipcash.app.persistence.sources.mapper.pools.NetworkPoolToEntityMapper
import com.flipcash.app.persistence.sources.mapper.pools.PoolBetEntityToPoolBetMapper
import com.flipcash.app.persistence.sources.mapper.pools.PoolBetMetadataParameters
import com.flipcash.app.persistence.sources.mapper.pools.PoolBetMetadataToEntityMapper
import com.flipcash.app.persistence.sources.mapper.pools.PoolEntityToPoolMapper
import com.flipcash.app.persistence.sources.mapper.pools.PoolMetadataMappingParameters
import com.flipcash.app.persistence.sources.mapper.pools.PoolMetadataToEntityMapper
import com.flipcash.services.models.NetworkPool
import com.flipcash.services.models.NetworkPoolBetOutcome
import com.flipcash.services.models.PoolBetMetadata
import com.flipcash.services.models.PoolMetadata
import com.flipcash.services.persistence.PagingDataSource
import com.flipcash.services.user.UserManager
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.model.core.ID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import kotlin.collections.map

class PoolDataSource @Inject constructor(
    private val poolEntityMapper: PoolEntityToPoolMapper,
    private val networkPoolToEntityMapper: NetworkPoolToEntityMapper,
    private val betEntityMapper: PoolBetEntityToPoolBetMapper,
    private val betMetadataEntityMapper: PoolBetMetadataToEntityMapper,
    private val poolMetadataToEntityMapper: PoolMetadataToEntityMapper,
    private val userManager: UserManager,
) : PagingDataSource<ID, PoolWithBets, List<NetworkPool>, Int, PoolWithBetsEntity> {

    private val db: FlipcashDatabase?
        get() = FlipcashDatabase.getInstance()

    override fun observe(): PagingSource<Int, PoolWithBetsEntity> {
        return db?.poolDao()?.observePools() ?: object : PagingSource<Int, PoolWithBetsEntity>() {
            override fun getRefreshKey(state: PagingState<Int, PoolWithBetsEntity>): Int? = null
            override suspend fun load(params: LoadParams<Int>): LoadResult<Int, PoolWithBetsEntity> =
                LoadResult.Error(Exception("Database not initialized"))
        }
    }

    fun observe(id: ID): Flow<PoolWithBets?> {
        return db?.poolDao()?.observe(id)?.map { entity ->
            val pool = entity?.let { poolEntityMapper.map(it.pool) } ?: return@map null
            val bets = entity.bets.map { betEntityMapper.map(it) }
            PoolWithBets(
                pool = pool,
                rendezvous = entity.rendezvous?.let { Ed25519.createKeyPair(it) },
                isHost = pool.creator == userManager.accountId,
                bets = bets
            )
        } ?: flowOf(null)
    }

    override suspend fun getById(id: ID): PoolWithBets? {
        val result = db?.poolDao()?.getPoolWithBets(id) ?: return null
        val pool =  poolEntityMapper.map(result.pool)
        val bets = result.bets.map { betEntityMapper.map(it) }
        return PoolWithBets(
            pool = pool,
            rendezvous = result.rendezvous?.let { Ed25519.createKeyPair(it) },
            isHost = pool.creator == userManager.accountId,
            bets = bets
        )
    }

    override suspend fun get(): List<PoolWithBets> {
        val result = db?.poolDao()?.getAll() ?: return emptyList()
        return result.map {
            val pool = poolEntityMapper.map(it.pool)
            val bets = it.bets.map { betEntityMapper.map(it) }
            PoolWithBets(
                pool = pool,
                rendezvous = null,
                isHost = pool.creator == userManager.accountId,
                bets = bets
            )
        }
    }

    override suspend fun upsert(value: List<NetworkPool>) {
        val entities = value.map { networkPoolToEntityMapper.map(it) }
        entities.onEach { (pool, bets) ->
            db?.withTransaction {
                db?.poolDao()?.upsert(pool)
                db?.poolDao()?.upsert(*bets.toTypedArray())
            }
        }
    }

    suspend fun upsert(pool: PoolMetadata) {
        val params = PoolMetadataMappingParameters(
            metadata = pool,
            pagingToken = null,
            selectedOutcome = null,
        )
        val entity = poolMetadataToEntityMapper.map(params)
        db?.poolDao()?.upsert(entity)
    }

    suspend fun upsertRendezvous(rendezvous: Ed25519.KeyPair, signature: List<Byte>? = null) {
        db?.poolDao()?.updateRendezvous(rendezvous, signature)
    }

    suspend fun resolvePool(id: ID, resolution: PoolResolution.DecisionMade) {
        db?.poolDao()?.resolvePool(id, resolution)
    }

    suspend fun unresolvePool(id: ID) {
        db?.poolDao()?.unresolvePool(id)
    }

    suspend fun closePool(id: ID) {
        db?.poolDao()?.closePool(id)
    }

    suspend fun reopenPool(id: ID) {
        db?.poolDao()?.reopenPool(id)
    }

    suspend fun upsertBet(poolId: ID, bet: PoolBetMetadata, hasSubmittedIntent: Boolean) {
        val params = PoolBetMetadataParameters(poolId, bet, hasSubmittedIntent)
        val entity = betMetadataEntityMapper.map(params)
        db?.poolDao()?.upsert(entity)
    }

    suspend fun removeBet(poolId: ID, betId: ID) {
        db?.poolDao()?.removeBet(poolId, betId)
    }

    override suspend fun query(whereClause: String): List<PoolWithBets> {
        return emptyList()
    }

    override suspend fun getMostRecent(): PoolWithBets? {
        val result = db?.poolDao()?.getNewestPool() ?: return null
        val pool = poolEntityMapper.map(result.pool)
        val bets = result.bets.map { betEntityMapper.map(it) }
        return PoolWithBets(
            pool = pool,
            rendezvous = null,
            isHost = pool.creator == userManager.accountId,
            bets = bets
        )
    }

    override suspend fun clear() {
        db?.poolDao()?.clear()
    }
}