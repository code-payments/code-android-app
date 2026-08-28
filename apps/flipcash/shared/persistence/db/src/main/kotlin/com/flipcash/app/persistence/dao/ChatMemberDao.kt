package com.flipcash.app.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.flipcash.app.persistence.converters.MessagePointerSerialized
import com.flipcash.app.persistence.entities.ChatMemberEntity
import com.flipcash.app.persistence.entities.ChatMemberWithProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMemberDao {

    @Transaction
    @Query("SELECT * FROM chat_members WHERE chat_id_hex = :chatIdHex")
    suspend fun getMembersForChat(chatIdHex: String): List<ChatMemberWithProfile>

    @Transaction
    @Query("SELECT * FROM chat_members WHERE chat_id_hex = :chatIdHex")
    fun observeMembersForChat(chatIdHex: String): Flow<List<ChatMemberWithProfile>>

    @Transaction
    @Query("SELECT * FROM chat_members")
    fun observeAll(): Flow<List<ChatMemberWithProfile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(entity: ChatMemberEntity)

    /**
     * Writes server truth for a member, keeping whichever copy of each pointer is further ahead.
     *
     * Deliberately not a whole-row REPLACE: `pointers_json` carries the READ pointer, which the
     * client advances locally the instant a message is seen and only reports afterwards. A feed
     * payload is the server's view as of the fetch, so replacing the row rewinds every advance
     * the server has not acknowledged yet — and the chat goes back to reporting unread.
     */
    @Transaction
    suspend fun upsert(entity: ChatMemberEntity) {
        val existing = getMember(entity.chatIdHex, entity.userIdHex)
            ?: return insertOrReplace(entity)

        insertOrReplace(
            entity.copy(
                pointersJson = mergePointers(existing.pointersJson, entity.pointersJson),
            )
        )
    }

    @Transaction
    suspend fun upsert(entities: List<ChatMemberEntity>) {
        for (entity in entities) upsert(entity)
    }

    @Query("SELECT * FROM chat_members WHERE chat_id_hex = :chatIdHex AND user_id_hex = :userIdHex LIMIT 1")
    suspend fun getMember(chatIdHex: String, userIdHex: String): ChatMemberEntity?

    /**
     * Resolves the chat id of a DM that [userIdHex] is a member of, of the given [chatType]
     * (e.g. `"TIP_DM"`). Reuses the already-synced `chat_members` ↔ `chat_metadata` data — no
     * extra column/table needed — and stays generic by filtering on the chat type.
     */
    @Query(
        """
        SELECT m.chat_id_hex FROM chat_members m
        INNER JOIN chat_metadata c ON c.chat_id_hex = m.chat_id_hex
        WHERE m.user_id_hex = :userIdHex AND c.chat_type = :chatType
        ORDER BY c.last_activity_epoch_ms DESC
        LIMIT 1
        """
    )
    suspend fun getChatIdForMember(userIdHex: String, chatType: String): String?

    @Query("UPDATE chat_members SET pointers_json = :pointersJson WHERE chat_id_hex = :chatIdHex AND user_id_hex = :userIdHex")
    suspend fun updatePointers(chatIdHex: String, userIdHex: String, pointersJson: String)

    @Query("DELETE FROM chat_members WHERE chat_id_hex = :chatIdHex")
    suspend fun deleteForChat(chatIdHex: String)

    /** Drops the members of [chatIdHex] that are no longer in [keepUserIdHexes]. */
    @Query(
        "DELETE FROM chat_members WHERE chat_id_hex = :chatIdHex " +
            "AND user_id_hex NOT IN (:keepUserIdHexes)"
    )
    suspend fun deleteMembersNotIn(chatIdHex: String, keepUserIdHexes: List<String>)

    @Query("DELETE FROM chat_members")
    suspend fun deleteAll()
}

/**
 * The pointers a member row should end up holding.
 *
 * A pointer only ever moves forward on either side — the client bumps its own READ pointer as
 * messages are seen, and the server only ever raises the copy it hands back — so taking the
 * greater value per (type, member) lets a refresh carry a pointer forward without rewinding a
 * local advance it has not been told about yet.
 */
private fun mergePointers(
    existing: List<MessagePointerSerialized>?,
    incoming: List<MessagePointerSerialized>?,
): List<MessagePointerSerialized>? {
    if (existing.isNullOrEmpty()) return incoming
    if (incoming.isNullOrEmpty()) return existing

    val merged = existing.associateByTo(LinkedHashMap()) { it.type to it.userIdHex }
    for (pointer in incoming) {
        val key = pointer.type to pointer.userIdHex
        val current = merged[key]
        if (current == null || pointer.value > current.value) {
            merged[key] = pointer
        }
    }
    return merged.values.toList()
}
