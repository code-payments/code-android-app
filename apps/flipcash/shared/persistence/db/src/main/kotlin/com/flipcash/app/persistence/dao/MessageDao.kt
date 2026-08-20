package com.flipcash.app.persistence.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.sqlite.db.SupportSQLiteQuery
import com.flipcash.app.persistence.entities.MessageEntity
import com.getcode.utils.base58
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE idBase58 = :idBase58")
    suspend fun getMessageById(idBase58: String): MessageEntity?
    suspend fun getMessageById(id: List<Byte>): MessageEntity? {
        return getMessageById(id.base58)
    }

    @RawQuery
    suspend fun queryDirectly(query: SupportSQLiteQuery): List<MessageEntity>

    @Query("SELECT * FROM messages ORDER BY timestamp DESC LIMIT 1")
    suspend fun getNewestMessage(): MessageEntity?

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(vararg messages: MessageEntity)

    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    fun observeMessages(): PagingSource<Int, MessageEntity>

    /** The [limit] most recent messages, newest first — the wallet's recent-activity preview. */
    @Query("SELECT * FROM messages ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<MessageEntity>>

    /**
     * The [limit] most recent messages for a single token, newest first — the token info screen's
     * per-token recent-activity preview.
     *
     * A convert spans two mints and belongs to both token histories, but a row carries only one
     * [MessageEntity.mintBase58] — the source mint, taken from the amount the user gave up. The
     * destination mint is only ever written into the serialized metadata (`SwappedCryptoMetadata`
     * persists it as a base58 string under `toMint`), so matching it there is what puts the convert
     * on the *receiving* token's screen too. Matching on the JSON rather than adding a column also
     * fixes rows that are already in the cache. Same LIKE-on-metadata approach as
     * [hasEverReceivedMoney]; base58 contains no LIKE wildcards, so the bound value is literal.
     *
     * The destination match is restricted to converts on purpose: a withdrawal can also carry swap
     * metadata, but there the destination leaves the app entirely, so it does not belong on the
     * destination token's history. `ActivityFeedCoordinator.involves` applies the same rule in memory.
     */
    @Query(
        "SELECT * FROM messages WHERE mintBase58 = :mintBase58 " +
            "OR (metadata LIKE '%com.flipcash.app.core.feed.MessageMetadata.SwappedCrypto%' " +
            "AND metadata LIKE '%\"toMint\":\"' || :mintBase58 || '\"%') " +
            "ORDER BY timestamp DESC LIMIT :limit"
    )
    fun observeRecentForMint(mintBase58: String, limit: Int): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages")
    suspend fun getAllMessages(): List<MessageEntity>

    /**
     * True once any completed *incoming* notification exists — the "added money" milestone.
     *
     * Money arriving is money arriving, regardless of how: an on-ramp buy, a deposit, or a tip
     * received from someone else. All three are the credit side of the feed (see
     * `MessageMetadata.isOutgoing`), so all three satisfy the milestone. Swaps are excluded — they
     * debit the source mint rather than bringing new money in.
     */
    @Query(
        "SELECT EXISTS(SELECT 1 FROM messages WHERE state = 'COMPLETED' AND (" +
            "metadata LIKE '%com.flipcash.app.core.feed.MessageMetadata.DepositedCrypto%' OR " +
            "metadata LIKE '%com.flipcash.app.core.feed.MessageMetadata.BoughtToken%' OR " +
            "metadata LIKE '%com.flipcash.app.core.feed.MessageMetadata.ReceivedCrypto%'))"
    )
    fun hasEverReceivedMoney(): Flow<Boolean>

    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()
}