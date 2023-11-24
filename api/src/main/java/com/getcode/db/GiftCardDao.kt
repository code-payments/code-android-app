package com.getcode.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.getcode.model.GiftCard
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Maybe


@Dao
interface GiftCardDao {
    @Query("SELECT * FROM GiftCard WHERE `key` = :key")
    fun get(key: String): Flowable<GiftCard>

    @Query("SELECT * FROM GiftCard WHERE `key` = :key")
    fun getMaybe(key: String): Maybe<GiftCard>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(item: GiftCard)
}