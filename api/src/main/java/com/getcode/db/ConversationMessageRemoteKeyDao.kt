package com.getcode.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.getcode.model.ConversationMessageRemoteKey
import com.getcode.model.ID
import com.getcode.network.repository.base58

@Dao
interface ConversationMessageRemoteKeyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(remoteKey: List<ConversationMessageRemoteKey>)
    @Query("SELECT * FROM messages_remote_keys WHERE messageIdBase58 = :id")
    suspend fun remoteKeysByMessageId(id: String): ConversationMessageRemoteKey?

    suspend fun remoteKeysByMessageId(id: ID): ConversationMessageRemoteKey? {
        return remoteKeysByMessageId(id.base58)
    }

    @Query("DELETE FROM messages_remote_keys")
    fun clearRemoteKeys()
}