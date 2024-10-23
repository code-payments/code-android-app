package com.getcode.oct24.domain.model.chat

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.getcode.model.ID
import com.getcode.model.chat.MessageContent
import com.getcode.model.chat.MessageStatus
import com.getcode.oct24.data.Member
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
    @ColumnInfo(defaultValue = "")
    val members: List<Member>,
    val lastActivity: Long?,
) {
    @Ignore
    val id: ID = Base58.decode(idBase58).toList()

    val name: String?
        get() = nonSelfMembers
            .mapNotNull { it.identity?.displayName }
            .joinToString()
            .takeIf { it.isNotEmpty() }

    val nonSelfMembers: List<Member>
        get() = members.filterNot { it.isSelf }

    override fun toString(): String {
        return """
            {
            id:${idBase58},
            title:$title,
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
    val conversationIdBase58: String,
    val dateMillis: Long,
) {
    @Ignore
    val id: ID = Base58.decode(idBase58).toList()

    @Ignore
    val conversationId: ID = Base58.decode(conversationIdBase58).toList()
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