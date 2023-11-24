package com.getcode.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.getcode.model.PrefDouble
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Maybe

@Dao
interface PrefDoubleDao {
    @Query("SELECT * FROM PrefDouble WHERE `key` = :key")
    fun get(key: String): Flowable<PrefDouble>

    @Query("SELECT * FROM PrefDouble WHERE `key` = :key")
    fun getMaybe(key: String): Maybe<PrefDouble>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(item: PrefDouble)
}