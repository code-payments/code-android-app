package com.flipcash.app.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.flipcash.app.persistence.entities.CurrencyCreatorDraftEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CurrencyCreatorDraftDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(draft: CurrencyCreatorDraftEntity): Long

    @Query("SELECT * FROM currency_creator_draft WHERE id = :id")
    suspend fun getById(id: Long): CurrencyCreatorDraftEntity?

    @Query("SELECT * FROM currency_creator_draft ORDER BY saved_at DESC")
    suspend fun getAll(): List<CurrencyCreatorDraftEntity>

    @Query("SELECT * FROM currency_creator_draft ORDER BY saved_at DESC")
    fun observeAll(): Flow<List<CurrencyCreatorDraftEntity>>

    @Query("DELETE FROM currency_creator_draft WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM currency_creator_draft")
    suspend fun deleteAll()
}
