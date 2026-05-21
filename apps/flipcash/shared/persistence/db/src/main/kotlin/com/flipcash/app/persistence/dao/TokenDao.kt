package com.flipcash.app.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.flipcash.app.persistence.entities.TokenEntity
import com.flipcash.app.persistence.entities.TokenValuationEntity
import com.flipcash.app.persistence.entities.TokenWithBalanceRelation
import kotlinx.coroutines.flow.Flow

@Dao
interface TokenDao {

    // region Token metadata

    @Query("SELECT * FROM tokens WHERE address = :address")
    suspend fun getByMint(address: String): TokenEntity?

    @Query("SELECT * FROM tokens WHERE symbol = :symbol COLLATE NOCASE LIMIT 1")
    suspend fun getBySymbol(symbol: String): TokenEntity?

    @Query("SELECT * FROM tokens")
    suspend fun getAll(): List<TokenEntity>

    @Query("SELECT * FROM tokens")
    fun observeAll(): Flow<List<TokenEntity>>

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTokens(vararg tokens: TokenEntity)

    @Query("DELETE FROM tokens")
    suspend fun clearTokens()

    // endregion

    // region Balances

    @Query("SELECT * FROM token_valuation WHERE token_address = :address")
    suspend fun getBalance(address: String): TokenValuationEntity?

    @Query("SELECT * FROM token_valuation")
    suspend fun getAllBalances(): List<TokenValuationEntity>

    @Query("SELECT * FROM token_valuation")
    fun observeBalances(): Flow<List<TokenValuationEntity>>

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBalances(vararg balances: TokenValuationEntity)

    @Query("DELETE FROM token_valuation")
    suspend fun clearBalances()

    // endregion

    // region Combined token + balance

    @Transaction
    @Query("SELECT * FROM tokens")
    suspend fun getTokensWithBalances(): List<TokenWithBalanceRelation>

    @Transaction
    @Query("SELECT * FROM tokens")
    fun observeTokensWithBalances(): Flow<List<TokenWithBalanceRelation>>

    // endregion

    @Transaction
    suspend fun clearAll() {
        clearTokens()
        clearBalances()
    }
}