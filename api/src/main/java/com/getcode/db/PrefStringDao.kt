package com.getcode.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.getcode.model.PrefString
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Maybe
import kotlinx.coroutines.flow.Flow

@Dao
interface PrefStringDao {
    @Query("SELECT * FROM PrefString WHERE key = :key")
    fun get(key: String): Flow<PrefString>

    @Query("SELECT * FROM PrefString WHERE key = :key")
    suspend fun getMaybe(key: String): PrefString?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: PrefString)
}
