package com.getcode.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.getcode.model.Rate
import com.getcode.model.ExchangeRate
import kotlinx.coroutines.flow.Flow

@Dao
interface ExchangeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rates: List<Rate>, syncedAt: Long) {
        insert(*rates.map { ExchangeRate(it.fx, it.currency, syncedAt) }.toTypedArray())
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg rate: ExchangeRate)

    @Query("SELECT * FROM exchangeData")
    fun observeRates(): Flow<List<ExchangeRate>>

    @Query("SELECT * FROM exchangeData")
    suspend fun query(): List<ExchangeRate>

    @Query("DELETE FROM exchangeData")
    fun clear()
}