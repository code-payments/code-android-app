package com.flipcash.app.persistence.sources

import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.room.withTransaction
import com.flipcash.app.core.pools.Pool
import com.flipcash.app.core.pools.PoolResolution
import com.flipcash.app.core.pools.PoolWithBets
import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.entities.PoolWithBetsEntity
import com.flipcash.app.persistence.sources.mapper.pools.NetworkPoolToEntityMapper
import com.flipcash.app.persistence.sources.mapper.pools.PoolBetEntityToPoolBetMapper
import com.flipcash.app.persistence.sources.mapper.pools.PoolBetMetadataToEntityMapper
import com.flipcash.app.persistence.sources.mapper.pools.PoolEntityToPoolMapper
import com.flipcash.services.models.NetworkPool
import com.flipcash.services.models.PoolBetMetadata
import com.flipcash.services.persistence.PagingDataSource
import com.flipcash.services.user.UserManager
import com.getcode.opencode.model.core.ID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class PoolDataSource @Inject constructor(
    private val poolEntityMapper: PoolEntityToPoolMapper,
    private val poolMetadataEntityMapper: NetworkPoolToEntityMapper,
    private val betEntityMapper: PoolBetEntityToPoolBetMapper,
    private val betMetadataEntityMapper: PoolBetMetadataToEntityMapper,
    private val userManager: UserManager,
) : PagingDataSource<ID, Pool, List<NetworkPool>, Int, PoolWithBetsEntity> {

    private val db: FlipcashDatabase?
        get() = FlipcashDatabase.getInstance()

    override fun observe(): PagingSource<Int, PoolWithBetsEntity> {
        return db?.poolDao()?.observePools() ?: object : PagingSource<Int, PoolWithBetsEntity>() {
            override fun getRefreshKey(state: PagingState<Int, PoolWithBetsEntity>): Int? = null
            override suspend fun load(params: LoadParams<Int>): LoadResult<Int, PoolWithBetsEntity> =
                LoadResult.Error(Exception("Database not initialized"))
        }
    }

    fun observe(id: ID): Flow<PoolWithBets> {
        return db?.poolDao()?.observe(id)?.map {
            val pool = poolEntityMapper.map(it.pool)
            val bets = it.bets.map { betEntityMapper.map(it) }
            PoolWithBets(pool, isHost = pool.creator == userManager.accountId, bets)
        } ?: throw Exception("No pool found")
    }

    override suspend fun getById(id: ID): Pool? {
        val result = db?.poolDao()?.getPoolWithBets(id) ?: return null
        return poolEntityMapper.map(result.pool)
    }


    suspend fun getByIdWithBets(id: ID): PoolWithBets? {
        val result = db?.poolDao()?.getPoolWithBets(id) ?: return null
        val pool = poolEntityMapper.map(result.pool)
        val bets = result.bets.map { betEntityMapper.map(it) }
        return PoolWithBets(
            pool = pool,
            isHost = pool.creator == userManager.accountId,
            bets = bets
        )
    }

    override suspend fun get(): List<Pool> {
        val result = db?.poolDao()?.getAll() ?: return emptyList()
        return result.map { poolEntityMapper.map(it) }
    }

    override suspend fun upsert(value: List<NetworkPool>) {
        val entities = value.map { poolMetadataEntityMapper.map(it) }
        entities.onEach { (pool, bets) ->
            db?.withTransaction {
                db?.poolDao()?.upsert(pool)
                db?.poolDao()?.upsert(*bets.toTypedArray())
            }
        }
    }

    suspend fun resolvePool(id: ID, resolution: PoolResolution) {
        db?.poolDao()?.resolvePool(id, resolution)
    }

    suspend fun closePool(id: ID) {
        db?.poolDao()?.closePool(id)

    }

    suspend fun addBet(poolId: ID, bet: PoolBetMetadata) {
        val entity = betMetadataEntityMapper.map(poolId to bet)
        db?.poolDao()?.upsert(entity)
    }

    override suspend fun query(whereClause: String): List<Pool> {
        return emptyList()
    }

    override suspend fun getMostRecent(): Pool? {
        val entity = db?.poolDao()?.getNewestPool() ?: return null
        return poolEntityMapper.map(entity)
    }

    override suspend fun clear() {
        db?.poolDao()?.clear()
    }
}