package xyz.flipchat.services.domain.model.chat

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.getcode.model.ID
import com.getcode.model.Kin
import com.getcode.model.chat.MessageContent
import com.getcode.model.chat.MessageStatus
import com.getcode.utils.serializer.KinQuarksSerializer
import com.getcode.vendor.Base58
import kotlinx.serialization.Serializable
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
    @ColumnInfo(defaultValue = "true")
    val canMute: Boolean,
    val unreadCount: Int,
    @ColumnInfo(name = "coverChargeQuarks")
    val messagingFee: Long?,
    @ColumnInfo(defaultValue = "false")
    val hasMoreUnread: Boolean,
    @ColumnInfo(defaultValue = "true")
    val isOpen: Boolean,
) {
    @Ignore
    val id: ID = Base58.decode(idBase58).toList()

    @Ignore
    val ownerId: ID? = ownerIdBase58?.let { Base58.decode(it).toList() }

    @Ignore
    @Serializable(with = KinQuarksSerializer::class)
    val coverCharge: Kin = messagingFee?.let { Kin.fromQuarks(messagingFee) } ?: Kin.fromQuarks(0)
}

data class ConversationWithMembersAndLastPointers(
    @Embedded val conversation: Conversation,
    @Relation(
        parentColumn = "idBase58",
        entityColumn = "conversationIdBase58",
        entity = ConversationMember::class
    )
    val members: List<ConversationMemberWithLinkedSocialProfiles>,
    @Relation(
        parentColumn = "idBase58",
        entityColumn = "conversationIdBase58",
        entity = ConversationPointerCrossRef::class,
    )
    val pointersCrossRef: List<ConversationPointerCrossRef>,
) {
    val pointers: Map<UUID, MessageStatus?>
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
    val members: List<ConversationMember>,
)


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
        projection = ["idBase58", "dateMillis", "senderIdBase58", "type", "content", "tipCount", "reactionCount", "isApproved", "sentOffStage"]
    )
    val lastMessage: ConversationMessage?
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
    val canChangeMuteState: Boolean
        get() = conversation.canMute
    val unreadCount: Int
        get() = conversation.unreadCount
    val hasMoreUnread: Boolean
        get() = conversation.hasMoreUnread

    val ownerId: ID?
        get() = conversation.ownerId

    val messageContentPreview: MessageContent?
        get() = lastMessage?.let { MessageContent.fromData(it.type, it.content, false) }

    @Ignore
    var pageIndex: Int? = null
}

