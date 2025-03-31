package com.getcode.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.getcode.services.model.PrefString
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Maybe
import kotlinx.coroutines.flow.Flow

@Dao
interface RxPrefStringDao {
    @Query("SELECT * FROM PrefString WHERE key = :key")
    fun get(key: String): Flowable<PrefString>

    @Query("SELECT * FROM PrefString WHERE key = :key")
    fun observe(key: String): Flow<PrefString?>

    @Query("SELECT * FROM PrefString WHERE key = :key")
    fun getMaybe(key: String): Maybe<PrefString>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: PrefString)
}