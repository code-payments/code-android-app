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
    val ownerIdBase58: String?,
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

    @Ignore
    val ownerId: ID? = ownerIdBase58?.let { Base58.decode(it).toList() }
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
    @Ignore
    private val nameCounts = members
        .mapNotNull { it.memberName }
        .groupingBy { it }
        .eachCount()
        .filter { it.value > 1 } // Only keep duplicates

    @Ignore
    val membersUnique: Map<ID, Int> = nameCounts.let { nameCounts ->
        val nameSuffixMap = mutableMapOf<String, Int>()
        members.reversed().associate { member ->
            val originalName = member.memberName
            val memberId = member.id

            // Assign a unique suffix if the name is a duplicate
            val suffix = if (originalName != null && nameCounts.containsKey(originalName)) {
                // Get the current suffix and increment it for the next use
                val currentSuffix = nameSuffixMap.getOrPut(originalName) { 1 }
                nameSuffixMap[originalName] = currentSuffix + 1
                currentSuffix
            } else {
                1 // Default suffix for unique names
            }

            memberId to suffix
        }
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

