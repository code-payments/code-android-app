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
    @Query("SELECT * FROM chat_members WHERE chat_id_hex = :chatIdHex AND is_member = 1")
    suspend fun getMembersForChat(chatIdHex: String): List<ChatMemberWithProfile>

    @Transaction
    @Query("SELECT * FROM chat_members WHERE chat_id_hex = :chatIdHex AND is_member = 1")
    fun observeMembersForChat(chatIdHex: String): Flow<List<ChatMemberWithProfile>>

    @Transaction
    @Query("SELECT * FROM chat_members WHERE is_member = 1")
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
        // A member who left stays out until a rejoin, the only change with a version above the
        // leave. A page that trails the stream still lists them, at their older join version.
        if (!existing.isMember && entity.version <= existing.version) return

        insertOrReplace(
            entity.copy(
                pointersJson = mergePointers(existing.pointersJson, entity.pointersJson),
                // Greater wins: a page that trails the stream must not wind a member back.
                version = maxOf(existing.version, entity.version),
                isMember = true,
            )
        )
    }

    /**
     * Applies a `MemberLeft` at roster [version]: [userIdHex] becomes a marker in [chatIdHex], so a
     * trailing roster page cannot re-add them. A leave older than the member's join is stale and
     * changes nothing. Pointers stay with the marker for a rejoin to carry on from.
     *
     * @return whether the leave applied, so the caller drops the member's search words only then.
     */
    @Transaction
    suspend fun markLeft(chatIdHex: String, userIdHex: String, version: Long): Boolean {
        val existing = getMember(chatIdHex, userIdHex)
        if (existing != null && existing.version > version) return false
        insertOrReplace(
            existing?.copy(version = version, isMember = false)
                ?: ChatMemberEntity(chatIdHex, userIdHex, pointersJson = null, version = version, isMember = false)
        )
        return true
    }

    /** Clears [chatIdHex]'s leave markers at or below [version]: a complete read at that version outranks them. */
    @Query("DELETE FROM chat_members WHERE chat_id_hex = :chatIdHex AND is_member = 0 AND version <= :version")
    suspend fun deleteMarkersUpTo(chatIdHex: String, version: Long)

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
        WHERE m.user_id_hex = :userIdHex AND m.is_member = 1 AND c.chat_type = :chatType
        ORDER BY c.last_activity_epoch_ms DESC
        LIMIT 1
        """
    )
    suspend fun getChatIdForMember(userIdHex: String, chatType: String): String?

    /**
     * Moves [userIdHex]'s pointer of [pointer]'s type forward in [chatIdHex], creating the member
     * row if it is not there yet. The member's other pointers carry over.
     *
     * The row is not a given at this point. A read is written the moment a message is on screen,
     * and a pointer update can arrive off the event stream for a member the feed has not written
     * yet — the bare `UPDATE` this replaces matched nothing in either case and dropped the
     * pointer silently.
     *
     * Forward only, on the same reasoning as [mergePointers]: the stream echoes a member's
     * pointer as the server last saw it, which can be behind a local advance that has not been
     * reported yet, and applying it would put the chat back to unread.
     */
    @Transaction
    suspend fun advancePointer(
        chatIdHex: String,
        userIdHex: String,
        pointer: MessagePointerSerialized,
    ) {
        val row = getMember(chatIdHex, userIdHex)
        val existing = row?.pointersJson.orEmpty()
        val current = existing.firstOrNull { it.type == pointer.type }
        if (current != null && current.value >= pointer.value) return

        val pointers = existing.filterNot { it.type == pointer.type } + pointer
        // Copied, not rebuilt: a fresh row would drop the member's version and re-add one who left.
        insertOrReplace(
            row?.copy(pointersJson = pointers)
                ?: ChatMemberEntity(chatIdHex = chatIdHex, userIdHex = userIdHex, pointersJson = pointers)
        )
    }

    @Query("DELETE FROM chat_members WHERE chat_id_hex = :chatIdHex")
    suspend fun deleteForChat(chatIdHex: String)

    /**
     * Drops the members of [chatIdHex] that are no longer in [keepUserIdHexes]. Leave markers stay:
     * they guard against a trailing roster page, which a feed refresh does not change.
     */
    @Query(
        "DELETE FROM chat_members WHERE chat_id_hex = :chatIdHex AND is_member = 1 " +
            "AND user_id_hex NOT IN (:keepUserIdHexes)"
    )
    suspend fun deleteMembersNotIn(chatIdHex: String, keepUserIdHexes: List<String>)

    /** Drops one member row of [chatIdHex], marker or not. A `MemberLeft` uses [markLeft] instead. */
    @Query("DELETE FROM chat_members WHERE chat_id_hex = :chatIdHex AND user_id_hex = :userIdHex")
    suspend fun deleteMember(chatIdHex: String, userIdHex: String)

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
