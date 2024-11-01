package com.getcode.oct24.domain.model.chat

import androidx.room.Entity
import androidx.room.Ignore
import com.getcode.model.ID
import com.getcode.model.chat.MessageStatus
import com.getcode.vendor.Base58
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.util.UUID


@Serializable
@Entity(tableName = "conversation_pointers", primaryKeys = ["conversationIdBase58", "status"])
data class ConversationPointerCrossRef(
    val conversationIdBase58: String,
    val messageIdString: String,
    val status: MessageStatus,
) {
    @Ignore
    val conversationId: ID = Base58.decode(conversationIdBase58).toList()

    @Ignore
    @Transient
    val messageId: UUID = UUID.fromString(messageIdString)
}