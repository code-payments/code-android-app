package com.getcode.oct24.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.getcode.services.model.PrefDouble
import kotlinx.coroutines.flow.Flow

@Dao
interface PrefDoubleDao {
    @Query("SELECT * FROM PrefDouble WHERE `key` = :key")
    suspend fun get(key: String): PrefDouble?

    @Query("SELECT * FROM PrefDouble WHERE key = :key")
    fun observe(key: String): Flow<PrefDouble?>


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: PrefDouble)
}