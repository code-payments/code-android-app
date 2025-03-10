package xyz.flipchat.services.domain.model.chat

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import com.getcode.model.ID
import com.getcode.model.KinAmount
import com.getcode.model.chat.MessageContent
import com.getcode.vendor.Base58
import kotlinx.serialization.Serializable
import xyz.flipchat.services.domain.model.people.FlipchatUser
import xyz.flipchat.services.domain.model.people.MemberPersonalInfo
import xyz.flipchat.services.domain.model.profile.MemberSocialProfile

@Serializable
@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["conversationIdBase58"]), // For filtering by conversation ID
        Index(value = ["senderIdBase58"]),       // For joining on sender ID
        Index(value = ["dateMillis"]),           // For ordering by date
    ]
)
data class ConversationMessage(
    @PrimaryKey
    val idBase58: String,
    val conversationIdBase58: String,
    @ColumnInfo(defaultValue = "")
    val senderIdBase58: String,
    val dateMillis: Long,
    private val deleted: Boolean?,
    private val deletedByBase58: String? = null,
    val inReplyToBase58: String? = null,
    @ColumnInfo(defaultValue = "false")
    val sentOffStage: Boolean = false,
    val isApproved: Boolean? = null,
    @ColumnInfo(defaultValue = "0")
    val tipCount: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val reactionCount: Int = 0,
    @ColumnInfo(defaultValue = "1")
    val type: Int,
    @ColumnInfo(defaultValue = "")
    val content: String,
) {
    fun getDeletedByBase58(): String? = deletedByBase58

    @Ignore
    val id: ID = Base58.decode(idBase58).toList()

    @Ignore
    val conversationId: ID = Base58.decode(conversationIdBase58).toList()

    @Ignore
    val senderId: ID = Base58.decode(senderIdBase58).toList()

    @Ignore
    val deletedBy: ID? = deletedByBase58?.let { Base58.decode(deletedByBase58).toList() }

    @Ignore
    val isDeleted: Boolean = deleted == true

    @Ignore
    val inReplyTo: ID? = inReplyToBase58?.let { Base58.decode(it).toList() }
}

data class ConversationMessageWithMemberAndReply(
    @Embedded val message: ConversationMessage,
    @Relation(
        parentColumn = "senderIdBase58",
        entityColumn = "memberIdBase58",
        entity = ConversationMember::class,
    )
    val member: ConversationMember?,
)


data class ConversationMessageWithMemberAndContent(
    @Embedded val message: ConversationMessage,

    // Member information from members table
    @Relation(
        parentColumn = "senderIdBase58",
        entityColumn = "memberIdBase58",
        entity = ConversationMember::class
    )
    val member: ConversationMember?,

    @Relation(
        parentColumn = "senderIdBase58",
        entityColumn = "userIdBase58",
        projection = ["memberName", "imageUri", "isBlocked"],
        entity = FlipchatUser::class,
    )
    val personalInfo: MemberPersonalInfo?
) {
    @Ignore
    var socialProfiles: List<MemberSocialProfile> = emptyList()
    @Ignore
    var contentEntity: MessageContent = MessageContent.Unknown(false)
}

data class InflatedConversationMessage(
    val pageIndex: Int = 0, // tracking for [PagingSource] refresh eky
    val message: ConversationMessage,
    val member: ConversationMemberWithLinkedSocialProfiles?,
    val content: MessageContent,
    val reply: ConversationMessageWithMemberAndContent?,
    val tips: List<MessageTipInfo>,
    val reactions: List<MessageReactionInfo>
)

data class MessageTipInfo(
    @Embedded val tip: ConversationMessageTip,
    @Relation(
        parentColumn = "tipperIdBase58",
        entityColumn = "memberIdBase58",
        entity = ConversationMember::class,
    )
    val tipper: ConversationMember?,
    @Relation(
        parentColumn = "tipperIdBase58",
        entityColumn = "userIdBase58",
        projection = ["memberName", "imageUri", "isBlocked"],
        entity = FlipchatUser::class
    )
    val personalInfo: MemberPersonalInfo?,

    @Relation(
        parentColumn = "tipperIdBase58",
        entityColumn = "memberIdBase58",
        entity = MemberSocialProfile::class
    )
    val socialProfiles: List<MemberSocialProfile>,
)

data class MessageReactionInfo(
    @Embedded val reaction: ConversationMessageReaction,
    @Relation(
        parentColumn = "senderIdBase58",
        entityColumn = "memberIdBase58",
        entity = ConversationMember::class,
    )
    val sender: ConversationMember?,
    @Relation(
        parentColumn = "senderIdBase58",
        entityColumn = "userIdBase58",
        projection = ["memberName", "imageUri", "isBlocked"],
        entity = FlipchatUser::class
    )
    val personalInfo: MemberPersonalInfo?,

    @Relation(
        parentColumn = "senderIdBase58",
        entityColumn = "memberIdBase58",
        entity = MemberSocialProfile::class
    )
    val socialProfiles: List<MemberSocialProfile>,
)