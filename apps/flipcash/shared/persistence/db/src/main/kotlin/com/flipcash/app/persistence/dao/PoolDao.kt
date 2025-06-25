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
import com.flipcash.app.persistence.entities.PoolRendezvousEntity
import com.flipcash.app.persistence.entities.PoolWithBetsAndRendezvousEntity
import com.flipcash.app.persistence.entities.PoolWithBetsEntity
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.model.core.ID
import com.getcode.utils.base58
import kotlinx.coroutines.flow.Flow

@Dao
interface PoolDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(vararg pool: PoolEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(vararg bet: PoolBetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(vararg rendezvousPair: PoolRendezvousEntity)

    suspend fun updateRendezvous(keypair: Ed25519.KeyPair, signature: List<Byte>? = null) {
        upsert(PoolRendezvousEntity(keypair.publicKeyBytes.base58, keypair.seed, signature?.base58))
    }

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
    suspend fun closePool(id: String)

    suspend fun closePool(id: ID) {
        closePool(id.base58)
    }

    @Query("SELECT * FROM pool_metadata ORDER BY timestamp DESC")
    suspend fun getAll(): List<PoolWithBetsEntity>

    @Query("SELECT * FROM pool_metadata ORDER BY isOpen DESC, timestamp DESC")
    fun observePools(): PagingSource<Int, PoolWithBetsEntity>

    @Query("SELECT * FROM pool_metadata WHERE idBase58 = :id ORDER BY timestamp DESC")
    fun observe(id: String): Flow<PoolWithBetsAndRendezvousEntity?>
    fun observe(id: ID): Flow<PoolWithBetsAndRendezvousEntity?> {
        return observe(id.base58)
    }

    @Transaction
    @Query("SELECT * FROM pool_metadata WHERE idBase58 = :id ORDER BY timestamp DESC")
    suspend fun getPoolWithBets(id: String): PoolWithBetsAndRendezvousEntity?
    suspend fun getPoolWithBets(id: ID): PoolWithBetsAndRendezvousEntity? {
        return getPoolWithBets(id.base58)
    }

    @Query("SELECT * FROM pool_metadata ORDER BY timestamp DESC LIMIT 1")
    suspend fun getNewestPool(): PoolWithBetsEntity?

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