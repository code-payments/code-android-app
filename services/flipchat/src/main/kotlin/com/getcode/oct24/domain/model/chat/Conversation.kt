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
import com.getcode.utils.base58
import com.getcode.vendor.Base58
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.util.UUID

@Serializable
@Entity(tableName = "conversations")
data class Conversation(
    @PrimaryKey
    val idBase58: String,
    val title: String,
    @ColumnInfo(defaultValue = "0")
    val roomNumber: Long,
    val imageUri: String?,
    val lastActivity: Long?,
    val isMuted: Boolean,
    val unreadCount: Int,
) {
    @Ignore
    val id: ID = Base58.decode(idBase58).toList()

    override fun toString(): String {
        return """
            {
            id:${idBase58},
            title:$title,
            image: $imageUri
            }
        """.trimIndent()
    }
}

@Serializable
data class ConversationWithMembersAndLastPointers(
    @Embedded val conversation: Conversation,
    @Relation(
        parentColumn = "idBase58",
        entityColumn = "conversationIdBase58"
    )
    val members: List<ConversationMember>,
    @Relation(
        parentColumn = "idBase58",
        entityColumn = "conversationIdBase58",
        entity = ConversationPointerCrossRef::class,
    )
    val pointersCrossRef: List<ConversationPointerCrossRef>,
) {
    fun nonSelfMembers(selfId: ID?): List<ConversationMember> {
        return members.filterNot { it.memberIdBase58 != selfId?.base58 }
    }

    val pointers: Map<UUID, MessageStatus>
        get() {
            return pointersCrossRef
                .associateBy { it.status }
                .mapKeys { it.value.messageId }
                .mapValues { it.value.status }
        }
}

data class ConversationWithMembers(
    @Embedded val conversation: Conversation,
    @Relation(
        parentColumn = "idBase58",
        entityColumn = "conversationIdBase58"
    )
    val members: List<ConversationMember>
) {
    val title: String
        get() = conversation.title
    val imageUri: String?
        get() = conversation.imageUri

    val lastActivity: Long?
        get() = conversation.lastActivity

    val isMuted: Boolean
        get() = conversation.isMuted

    val unreadCount: Int
        get() = conversation.unreadCount

    fun nonSelfMembers(selfId: ID?): List<ConversationMember> {
        return members.filterNot { it.memberIdBase58 != selfId?.base58 }
    }
}

data class ConversationWithMembersAndLastMessage(
    @Embedded val conversation: Conversation,
    @Relation(
        parentColumn = "idBase58",
        entityColumn = "conversationIdBase58"
    )
    val members: List<ConversationMember>,
    @Relation(
        parentColumn = "idBase58",
        entityColumn = "conversationIdBase58",
        entity = ConversationMessage::class,
        projection = ["idBase58", "dateMillis", "senderIdBase58"]
    )
    val lastMessage: ConversationMessageWithContent?
) {
    val id: ID
        get() = conversation.id
    val title: String
        get() = conversation.title
    val imageUri: String?
        get() = conversation.imageUri
    val lastActivity: Long?
        get() = conversation.lastActivity
    val isMuted: Boolean
        get() = conversation.isMuted
    val unreadCount: Int
        get() = conversation.unreadCount

    fun nonSelfMembers(selfId: ID?): List<ConversationMember> {
        return members.filterNot { it.memberIdBase58 != selfId?.base58 }
    }
}

