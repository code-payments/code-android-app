package com.flipcash.app.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.flipcash.app.persistence.entities.ChatMemberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMemberDao {

    @Query("SELECT * FROM chat_members WHERE chat_id_hex = :chatIdHex")
    suspend fun getMembersForChat(chatIdHex: String): List<ChatMemberEntity>

    @Query("SELECT * FROM chat_members WHERE chat_id_hex = :chatIdHex")
    fun observeMembersForChat(chatIdHex: String): Flow<List<ChatMemberEntity>>

    @Query("SELECT * FROM chat_members")
    fun observeAll(): Flow<List<ChatMemberEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ChatMemberEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entities: List<ChatMemberEntity>)

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

    @Query("DELETE FROM chat_members")
    suspend fun deleteAll()
}
