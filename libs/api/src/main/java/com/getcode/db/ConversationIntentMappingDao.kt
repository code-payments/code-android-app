package com.getcode.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.getcode.model.ConversationIntentIdReference
import com.getcode.model.ID
import com.getcode.network.repository.base58

@Dao
interface ConversationIntentMappingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mapping: ConversationIntentIdReference)

    @Query("SELECT * FROM conversation_intent_id_mapping WHERE intentIdBase58 = :id")
    suspend fun conversationIdByReference(id: String): ConversationIntentIdReference?

    suspend fun conversationIdByReference(id: ID): ConversationIntentIdReference? {
        return conversationIdByReference(id.base58)
    }

    @Query("DELETE FROM conversation_intent_id_mapping WHERE conversationIdBase58 NOT IN (:chatIds)")
    suspend fun purgeMappingNoLongerNeededByString(chatIds: List<String>)

    suspend fun purgeMappingNoLongerNeeded(chatIds: List<ID>) {
        purgeMappingNoLongerNeededByString(chatIds.map { it.base58 })
    }

    @Query("DELETE FROM conversation_intent_id_mapping")
    suspend fun clearMapping()
}