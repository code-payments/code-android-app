package com.flipcash.app.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.flipcash.app.persistence.entities.ChatDraftEntity

@Dao
interface ChatDraftDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(draft: ChatDraftEntity)

    @Query("SELECT * FROM chat_draft WHERE chat_id_hex = :chatIdHex")
    suspend fun getByChatId(chatIdHex: String): ChatDraftEntity?

    @Query("DELETE FROM chat_draft WHERE chat_id_hex = :chatIdHex")
    suspend fun deleteByChatId(chatIdHex: String)

    @Query("DELETE FROM chat_draft")
    suspend fun deleteAll()
}
