package com.getcode.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.getcode.model.PrefInt
import kotlinx.coroutines.flow.Flow

@Dao
interface PrefIntDao {
    @Query("SELECT * FROM PrefInt WHERE key = :key")
    fun get(key: String): Flow<PrefInt>

    @Query("SELECT * FROM PrefInt WHERE key = :key")
    suspend fun getMaybe(key: String): PrefInt?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: PrefInt)
}