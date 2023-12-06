package com.getcode.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.getcode.model.PrefBool
import kotlinx.coroutines.flow.Flow

@Dao
interface PrefBoolDao {
    @Query("SELECT * FROM PrefBool WHERE key = :key")
    fun get(key: String): Flow<PrefBool>

    @Query("SELECT * FROM PrefBool WHERE key = :key")
    suspend fun getMaybe(key: String): PrefBool?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: PrefBool)
}