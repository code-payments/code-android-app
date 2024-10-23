package com.getcode.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.getcode.services.model.PrefDouble
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Maybe
import kotlinx.coroutines.flow.Flow

@Dao
interface RxPrefDoubleDao {
    @Query("SELECT * FROM PrefDouble WHERE `key` = :key")
    fun get(key: String): Flowable<PrefDouble>

    @Query("SELECT * FROM PrefDouble WHERE key = :key")
    fun observe(key: String): Flow<PrefDouble?>

    @Query("SELECT * FROM PrefDouble WHERE `key` = :key")
    fun getMaybe(key: String): Maybe<PrefDouble>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: PrefDouble)
}