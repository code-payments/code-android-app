package com.getcode.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.getcode.model.chat.ChatMember
import com.getcode.model.chat.MessageContent
import com.getcode.utils.serializer.MessageContentSerializer
import com.getcode.vendor.Base58
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.util.UUID

@Serializable
@Entity(tableName = "conversations")
data class Conversation(
    @PrimaryKey
    val idBase58: String,
    val title: String?,
    val hasRevealedIdentity: Boolean,
    @ColumnInfo(defaultValue = "")
    val members: List<ChatMember>,
    val lastActivity: Long?,
) {
    @Ignore
    val id: ID = Base58.decode(idBase58).toList()

    val name: String?
        get() = nonSelfMembers
            .mapNotNull { it.identity?.username }
            .joinToString()
            .takeIf { it.isNotEmpty() }

    val nonSelfMembers: List<ChatMember>
        get() = members.filterNot { it.isSelf }

    override fun toString(): String {
        return """
            {
            id:${idBase58},
            title:$title,
            hasRevealedIdentity:$hasRevealedIdentity,
            members:${members.joinToString()}
            }
        """.trimIndent()
    }
}

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

@Serializable
data class ConversationWithLastPointers(
    @Embedded val conversation: Conversation,
    @Relation(
        parentColumn = "idBase58",
        entityColumn = "conversationIdBase58",
        entity = ConversationPointerCrossRef::class,
    )
    val pointersCrossRef: List<ConversationPointerCrossRef>
) {
    val pointers: Map<UUID, MessageStatus>
        get() {
            return pointersCrossRef
                .associateBy { it.status }
                .mapKeys { it.value.messageId }
                .mapValues { it.value.status }
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
    val contents: List<MessageContent>,
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
    Sent, Delivered, Read, Unknown;

    fun isOutgoing() = when (this) {
        Sent,
        Delivered -> true

        else -> false
    }

    fun isValid() = this != Unknown
}