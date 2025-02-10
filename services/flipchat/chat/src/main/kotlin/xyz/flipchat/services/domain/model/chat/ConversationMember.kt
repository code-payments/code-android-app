package xyz.flipchat.services.domain.model.chat

import androidx.room.ColumnInfo
import androidx.room.DatabaseView
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.Relation
import com.getcode.model.ID
import com.getcode.vendor.Base58
import kotlinx.serialization.Serializable
import xyz.flipchat.services.domain.model.people.FlipchatUser
import xyz.flipchat.services.domain.model.people.MemberPersonalInfo

@Serializable
@Entity(
    tableName = "members",
    primaryKeys = ["memberIdBase58", "conversationIdBase58"],
    indices = [
        Index(value = ["memberIdBase58"]),
        Index(value = ["conversationIdBase58"])
    ]
)
data class ConversationMember(
    val memberIdBase58: String,
    val conversationIdBase58: String,
    @ColumnInfo(defaultValue = "false")
    val isHost: Boolean, // isModerator
    @ColumnInfo(defaultValue = "false")
    val isMuted: Boolean,
    @ColumnInfo(defaultValue = "false")
    val isFullMember: Boolean,
) {
    @Ignore
    val id: ID = Base58.decode(memberIdBase58).toList()

    @Ignore
    val conversationId: ID = Base58.decode(conversationIdBase58).toList()
}

data class ConversationMemberWithPersonalInfo(
    @Embedded val member: ConversationMember?,
    @Relation(
        parentColumn = "memberIdBase58",
        entityColumn = "userIdBase58",
        projection = ["memberName", "imageUri", "isBlocked"],
        entity = FlipchatUser::class
    )
    private val personalInfo: MemberPersonalInfo?,
) {
    val id: ID? get() = member?.memberIdBase58?.let { Base58.decode(it).toList() }

    val isHost: Boolean get() = member?.isHost == true

    val isMuted: Boolean get() = member?.isMuted == true

    val isFullMember: Boolean get() = member?.isFullMember == true

    val displayName: String? get() = personalInfo?.memberName

    val imageUri: String? get() = personalInfo?.imageUri

    val isBlocked: Boolean get() = personalInfo?.isBlocked == true
}