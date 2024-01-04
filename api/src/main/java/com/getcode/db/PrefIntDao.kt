package com.getcode.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.getcode.model.PrefBool
import com.getcode.model.PrefInt
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Maybe
import kotlinx.coroutines.flow.Flow

@Dao
interface PrefIntDao {
    @Query("SELECT * FROM PrefInt WHERE key = :key")
    fun get(key: String): Flowable<PrefInt>

    @Query("SELECT * FROM PrefInt WHERE key = :key")
    fun observe(key: String): Flow<PrefInt?>

    @Query("SELECT * FROM PrefInt WHERE key = :key")
    fun getMaybe(key: String): Maybe<PrefInt>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: PrefInt)
}