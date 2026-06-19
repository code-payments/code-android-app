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

    @Query("UPDATE chat_members SET pointers_json = :pointersJson WHERE chat_id_hex = :chatIdHex AND user_id_hex = :userIdHex")
    suspend fun updatePointers(chatIdHex: String, userIdHex: String, pointersJson: String)

    @Query("DELETE FROM chat_members WHERE chat_id_hex = :chatIdHex")
    suspend fun deleteForChat(chatIdHex: String)

    @Query("DELETE FROM chat_members")
    suspend fun deleteAll()
}
