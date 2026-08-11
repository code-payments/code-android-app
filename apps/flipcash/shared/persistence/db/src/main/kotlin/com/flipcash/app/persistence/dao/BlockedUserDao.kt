package com.flipcash.app.persistence.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.flipcash.app.persistence.entities.BlockedUserEntity
import com.flipcash.app.persistence.entities.BlockedUserWithProfile

@Dao
interface BlockedUserDao {

    /** Blocklist ordered most-recently-blocked first, matching the server's ordering. */
    @Transaction
    @Query("SELECT * FROM blocked_users ORDER BY blocked_at_epoch_ms DESC")
    fun observePaged(): PagingSource<Int, BlockedUserWithProfile>

    @Transaction
    @Query("SELECT * FROM blocked_users ORDER BY blocked_at_epoch_ms DESC")
    suspend fun getAll(): List<BlockedUserWithProfile>

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entities: List<BlockedUserEntity>)

    /**
     * Atomically replaces the whole blocklist (used by a paging REFRESH). Doing the clear + insert
     * in one transaction means Room fires a single invalidation with the final rows — so observers
     * never momentarily see an empty table between the delete and the insert.
     */
    @Transaction
    suspend fun replaceAll(entities: List<BlockedUserEntity>) {
        deleteAll()
        upsert(entities)
    }

    @Query("DELETE FROM blocked_users WHERE user_id_hex = :userIdHex")
    suspend fun delete(userIdHex: String)

    @Query("DELETE FROM blocked_users")
    suspend fun deleteAll()
}
