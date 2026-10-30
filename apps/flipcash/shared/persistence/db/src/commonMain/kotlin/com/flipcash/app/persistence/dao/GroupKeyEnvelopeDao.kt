package com.flipcash.app.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.flipcash.app.persistence.entities.GroupKeyEnvelopeEntity

@Dao
interface GroupKeyEnvelopeDao {

    /** Stores [entity], replacing the row for the same chat and own key. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: GroupKeyEnvelopeEntity)

    @Query("SELECT * FROM group_key_envelopes WHERE chat_id_hex = :chatIdHex AND own_pk_hex = :ownPkHex")
    suspend fun find(chatIdHex: String, ownPkHex: String): GroupKeyEnvelopeEntity?

    @Query("DELETE FROM group_key_envelopes WHERE chat_id_hex = :chatIdHex AND own_pk_hex = :ownPkHex")
    suspend fun delete(chatIdHex: String, ownPkHex: String)

    /** Removes every envelope stored for [chatIdHex], whichever own key it was wrapped for. */
    @Query("DELETE FROM group_key_envelopes WHERE chat_id_hex = :chatIdHex")
    suspend fun deleteForChat(chatIdHex: String)

    @Query("DELETE FROM group_key_envelopes")
    suspend fun deleteAll()
}
