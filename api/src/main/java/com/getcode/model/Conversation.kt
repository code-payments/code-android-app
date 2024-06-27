package com.getcode.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.getcode.model.chat.MessageContent
import com.getcode.utils.serializer.MessageContentSerializer
import com.getcode.vendor.Base58
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "conversations")
data class Conversation(
    @PrimaryKey
    val idBase58: String,
    @ColumnInfo(defaultValue = "Tip Chat")
    val title: String,
    val hasRevealedIdentity: Boolean,
    val user: String?,
    val userImage: String?,
    val lastActivity: Long?,
) {
    @Ignore
    val id: ID = Base58.decode(idBase58).toList()

    override fun toString(): String {
        return """
            {
            id:${idBase58},
            title:$title,
            hasRevealedIdentity:$hasRevealedIdentity,
            user:$user,
            }
        """.trimIndent()
    }
}

@Serializable
@Entity(tableName = "messages")
data class ConversationMessage(
    @PrimaryKey
    val idBase58: String,
    val cursorBase58: String,
    val conversationIdBase58: String,
    val dateMillis: Long,
    @ColumnInfo(defaultValue = "Unknown")
    val status: MessageStatus,
) {
    @Ignore
    val id: ID = Base58.decode(idBase58).toList()
    @Ignore
    val conversationId: ID = Base58.decode(conversationIdBase58).toList()
    @Ignore
    val cursor: Cursor = Base58.decode(cursorBase58).toList()
}

@Serializable
@Entity(tableName = "message_contents", primaryKeys = ["messageIdBase58", "content"])
data class ConversationMessageContent(
    val messageIdBase58: String,
    val content: MessageContent
)

data class ConversationMessageWithContent(
    @Embedded val message: ConversationMessage,
    @Relation(
        parentColumn = "idBase58",
        entityColumn = "messageIdBase58",
        entity = ConversationMessageContent::class,
        projection = ["content"]
    )
    val contents: List<MessageContent>
)

@Entity(tableName = "conversation_intent_id_mapping")
data class ConversationIntentIdReference(
    @PrimaryKey
    val conversationIdBase58: String,
    val intentIdBase58: String,
) {
    @Ignore
    val conversationId: ID = Base58.decode(conversationIdBase58).toList()
    @Ignore
    val intentId: ID = Base58.decode(intentIdBase58).toList()
}

enum class MessageStatus {
    Incoming, Sent, Delivered, Read, Unknown;

    fun isOutgoing() = when (this) {
        Incoming -> false
        Sent,
        Delivered,
        Read -> true
        Unknown -> false
    }
    fun isValid() = this != Unknown
}