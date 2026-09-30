package com.flipcash.app.persistence.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.flipcash.app.persistence.entities.ChatMemberSearchTokenEntity
import com.flipcash.app.persistence.entities.ChatRosterSyncEntity
import com.flipcash.services.models.chat.MediaItem

/**
 * The member search index of each chat, and how much of each group's roster has been read into it.
 *
 * Normalizing names into tokens is the caller's job. SQLite cannot fold diacritics, so the index
 * holds already-normalized words and a query must be normalized the same way before it gets here.
 */
@Dao
interface ChatMemberSearchDao {

    // region tokens

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTokens(tokens: List<ChatMemberSearchTokenEntity>)

    @Query("DELETE FROM chat_member_search_tokens WHERE chat_id_hex = :chatIdHex AND user_id_hex = :userIdHex")
    suspend fun deleteTokensForMember(chatIdHex: String, userIdHex: String)

    /** Swaps [userIdHex]'s words in [chatIdHex] for [tokens], so a renamed member loses the old ones. */
    @Transaction
    suspend fun replaceTokens(chatIdHex: String, userIdHex: String, tokens: Collection<String>) {
        deleteTokensForMember(chatIdHex, userIdHex)
        insertTokens(tokens.map { ChatMemberSearchTokenEntity(chatIdHex, userIdHex, it) })
    }

    @Query("SELECT token FROM chat_member_search_tokens WHERE chat_id_hex = :chatIdHex AND user_id_hex = :userIdHex")
    suspend fun getTokens(chatIdHex: String, userIdHex: String): List<String>

    @Query("SELECT chat_id_hex FROM chat_members WHERE user_id_hex = :userIdHex")
    suspend fun getChatIdsForMember(userIdHex: String): List<String>

    /**
     * Swaps [userIdHex]'s words for [tokens] in every chat they are a member of. For a profile
     * written outside a roster write, which changes their name everywhere at once.
     */
    @Transaction
    suspend fun replaceTokensEverywhere(userIdHex: String, tokens: Collection<String>) {
        for (chatIdHex in getChatIdsForMember(userIdHex)) replaceTokens(chatIdHex, userIdHex, tokens)
    }

    /** Drops the words of anyone in [chatIdHex]'s index who is no longer one of its members. */
    @Query(
        "DELETE FROM chat_member_search_tokens WHERE chat_id_hex = :chatIdHex " +
            "AND user_id_hex NOT IN (SELECT user_id_hex FROM chat_members WHERE chat_id_hex = :chatIdHex)"
    )
    suspend fun deleteTokensOfFormerMembers(chatIdHex: String)

    @Query("DELETE FROM chat_member_search_tokens WHERE chat_id_hex = :chatIdHex")
    suspend fun deleteTokensForChat(chatIdHex: String)

    @Query("DELETE FROM chat_member_search_tokens")
    suspend fun deleteAllTokens()

    // endregion

    // region search

    /**
     * Members of [chatIdHex] other than [selfIdHex] with a word in [lower, upper), each with when
     * they last spoke among the chat's newest [recentWindow] held messages (null if they did not).
     *
     * Unordered: ranking folds diacritics the way the index does, which SQLite's collations cannot,
     * so it happens in Kotlin. The token subquery is a range scan on
     * `index_chat_member_search_tokens_chat_id_hex_token`; the recent-speaker subquery reads the
     * newest rows of `index_chat_messages_chat_id_hex_timestamp_epoch_ms`.
     */
    @Query(
        """
        SELECT m.user_id_hex AS user_id_hex, p.display_name AS display_name, p.username AS username,
            p.profile_picture_json AS profile_picture_json, r.last_spoke_epoch_ms AS last_spoke_epoch_ms
        FROM chat_members m
        LEFT JOIN user_profiles p ON p.user_id_hex = m.user_id_hex
        LEFT JOIN (
            SELECT sender_id_hex, MAX(timestamp_epoch_ms) AS last_spoke_epoch_ms FROM (
                SELECT sender_id_hex, timestamp_epoch_ms FROM chat_messages
                WHERE chat_id_hex = :chatIdHex AND sender_id_hex IS NOT NULL
                ORDER BY timestamp_epoch_ms DESC LIMIT :recentWindow
            ) GROUP BY sender_id_hex
        ) r ON r.sender_id_hex = m.user_id_hex
        WHERE m.chat_id_hex = :chatIdHex AND m.user_id_hex != :selfIdHex
            AND m.user_id_hex IN (
                SELECT user_id_hex FROM chat_member_search_tokens
                WHERE chat_id_hex = :chatIdHex AND token >= :lower AND token < :upper
            )
        """
    )
    suspend fun searchByTokenRange(
        chatIdHex: String,
        selfIdHex: String,
        lower: String,
        upper: String,
        recentWindow: Int,
    ): List<MemberSearchRow>

    /** Current members of [chatIdHex] other than [selfIdHex] who sent one of its newest [recentWindow] held messages. */
    @Query(
        """
        SELECT m.user_id_hex AS user_id_hex, p.display_name AS display_name, p.username AS username,
            p.profile_picture_json AS profile_picture_json, r.last_spoke_epoch_ms AS last_spoke_epoch_ms
        FROM chat_members m
        INNER JOIN (
            SELECT sender_id_hex, MAX(timestamp_epoch_ms) AS last_spoke_epoch_ms FROM (
                SELECT sender_id_hex, timestamp_epoch_ms FROM chat_messages
                WHERE chat_id_hex = :chatIdHex AND sender_id_hex IS NOT NULL
                ORDER BY timestamp_epoch_ms DESC LIMIT :recentWindow
            ) GROUP BY sender_id_hex
        ) r ON r.sender_id_hex = m.user_id_hex
        LEFT JOIN user_profiles p ON p.user_id_hex = m.user_id_hex
        WHERE m.chat_id_hex = :chatIdHex AND m.user_id_hex != :selfIdHex
        """
    )
    suspend fun recentSpeakers(chatIdHex: String, selfIdHex: String, recentWindow: Int): List<MemberSearchRow>

    // endregion

    // region roster sync

    @Query("SELECT COUNT(*) FROM chat_members WHERE chat_id_hex = :chatIdHex")
    suspend fun countMembers(chatIdHex: String): Int

    /** Members of [chatIdHex] who joined at or before [version]: the ones a full read may drop. */
    @Query("SELECT user_id_hex FROM chat_members WHERE chat_id_hex = :chatIdHex AND version <= :version")
    suspend fun getMemberIdsJoinedBy(chatIdHex: String, version: Long): List<String>

    @Query("SELECT * FROM chat_roster_sync WHERE chat_id_hex = :chatIdHex LIMIT 1")
    suspend fun getSyncState(chatIdHex: String): ChatRosterSyncEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSyncState(state: ChatRosterSyncEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSyncStateIfAbsent(state: ChatRosterSyncEntity)

    @Query("UPDATE chat_roster_sync SET watermark = :watermark WHERE chat_id_hex = :chatIdHex")
    suspend fun setWatermark(chatIdHex: String, watermark: Long)

    /**
     * Moves [chatIdHex]'s watermark from [from] to [to], and only from [from]: a stream change
     * applied on top of a roster the device held in full keeps it in full. From anywhere else the
     * watermark stays put, and the next catch-up reads down to it.
     */
    @Query("UPDATE chat_roster_sync SET watermark = :to WHERE chat_id_hex = :chatIdHex AND watermark = :from")
    suspend fun advanceWatermark(chatIdHex: String, from: Long, to: Long)

    @Query("UPDATE chat_roster_sync SET reconcile_pending = 1 WHERE chat_id_hex = :chatIdHex")
    suspend fun setReconcilePending(chatIdHex: String)

    @Query("DELETE FROM chat_roster_sync WHERE chat_id_hex = :chatIdHex")
    suspend fun deleteSyncState(chatIdHex: String)

    @Query("DELETE FROM chat_roster_sync")
    suspend fun deleteAllSyncState()

    // endregion
}

/** A member a search found, with what ranking needs to order it. */
data class MemberSearchRow(
    @ColumnInfo(name = "user_id_hex") val userIdHex: String,
    // Null when the member's profile has not been written yet.
    @ColumnInfo(name = "display_name") val displayName: String?,
    @ColumnInfo(name = "username") val username: String?,
    @ColumnInfo(name = "profile_picture_json") val profilePicture: MediaItem?,
    // Null when the member sent none of the chat's newest held messages.
    @ColumnInfo(name = "last_spoke_epoch_ms") val lastSpokeEpochMs: Long?,
)
