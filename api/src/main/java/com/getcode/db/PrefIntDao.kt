package com.getcode.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.getcode.model.PrefInt
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Maybe

@Dao
interface PrefIntDao {
    @Query("SELECT * FROM PrefInt WHERE key = :key")
    fun get(key: String): Flowable<PrefInt>

    @Query("SELECT * FROM PrefInt WHERE key = :key")
    fun getMaybe(key: String): Maybe<PrefInt>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(item: PrefInt)
}