package com.getcode.oct24.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.getcode.oct24.domain.model.chat.ConversationPointerCrossRef
import com.getcode.model.ID
import com.getcode.model.chat.MessageStatus
import com.getcode.utils.base58

import java.util.UUID

@Dao
interface ConversationPointerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(crossRef: ConversationPointerCrossRef)

    suspend fun insert(conversationId: ID, messageId: UUID, status: MessageStatus) {
        insert(ConversationPointerCrossRef(conversationId.base58, messageId.toString(), status))
    }

    @Query("SELECT * FROM conversation_pointers")
    suspend fun queryPointers(): List<ConversationPointerCrossRef>

    @Query("DELETE FROM conversation_pointers WHERE conversationIdBase58 = :id")
    suspend fun deletePointerForConversation(id: String)

    suspend fun deletePointerForConversation(id: ID) {
        deletePointerForConversation(id.base58)
    }

    @Query("DELETE FROM conversation_pointers WHERE conversationIdBase58 NOT IN (:chatIds)")
    suspend fun purgePointersNoLongerNeededByString(chatIds: List<String>)

    suspend fun purgePointersNoLongerNeeded(chatIds: List<ID>) {
        purgePointersNoLongerNeededByString(chatIds.map { it.base58 })
    }

    @Query("DELETE FROM conversation_pointers")
    suspend fun clearMapping()
}