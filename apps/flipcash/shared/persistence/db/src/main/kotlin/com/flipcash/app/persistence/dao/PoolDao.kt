package com.flipcash.app.persistence.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.flipcash.app.core.pools.PoolResolution
import com.flipcash.app.persistence.entities.PoolBetEntity
import com.flipcash.app.persistence.entities.PoolEntity
import com.flipcash.app.persistence.entities.PoolWithBetsEntity
import com.getcode.opencode.model.core.ID
import com.getcode.utils.base58
import kotlinx.coroutines.flow.Flow

@Dao
interface PoolDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(vararg pool: PoolEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(vararg bet: PoolBetEntity)

    @Query("UPDATE pool_metadata SET resolution = :resolution WHERE idBase58 = :id")
    suspend fun resolvePool(
        id: String,
        resolution: PoolResolution,
    )

    suspend fun resolvePool(
        id: ID,
        resolution: PoolResolution,
    ) {
        resolvePool(id.base58, resolution)
    }

    @Query("UPDATE pool_metadata SET isOpen = 0 AND closedTimestamp = strftime('%s', 'now') WHERE idBase58 = :id")
    suspend fun closePool(
        id: String,
    )

    suspend fun closePool(
        id: ID,
    ) {
        closePool(id.base58)
    }

    @Query("SELECT * FROM pool_metadata ORDER BY timestamp DESC")
    suspend fun getAll(): List<PoolEntity>

    @Query("SELECT * FROM pool_metadata ORDER BY isOpen DESC")
    fun observePools(): PagingSource<Int, PoolWithBetsEntity>

    @Query("SELECT * FROM pool_metadata WHERE idBase58 = :id ORDER BY timestamp DESC")
    fun observe(id: String): Flow<PoolWithBetsEntity>
    fun observe(id: ID): Flow<PoolWithBetsEntity> {
        return observe(id.base58)
    }

    @Transaction
    @Query("SELECT * FROM pool_metadata WHERE idBase58 = :id ORDER BY timestamp DESC")
    suspend fun getPoolWithBets(id: String): PoolWithBetsEntity?
    suspend fun getPoolWithBets(id: ID): PoolWithBetsEntity? {
        return getPoolWithBets(id.base58)
    }

    @Query("SELECT * FROM pool_metadata ORDER BY timestamp DESC LIMIT 1")
    suspend fun getNewestPool(): PoolEntity?

    @Query("DELETE FROM pool_metadata")
    suspend fun deleteAllPools()

    @Query("DELETE FROM pool_bet_metadata")
    suspend fun deleteAllPoolBets()

    @Transaction
    suspend fun clear() {
        deleteAllPools()
        deleteAllPoolBets()
    }
}