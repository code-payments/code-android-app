package xyz.flipchat.services.domain.model.chat

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import com.getcode.model.ID
import com.getcode.vendor.Base58
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "members",
    primaryKeys = ["memberIdBase58", "conversationIdBase58"]
)
data class ConversationMember(
    val memberIdBase58: String,
    val conversationIdBase58: String,
    val memberName: String?,
    val imageUri: String?,
    @ColumnInfo(defaultValue = "false")
    val isHost: Boolean,
    @ColumnInfo(defaultValue = "false")
    val isMuted: Boolean,
) {
    @Ignore
    val id: ID = Base58.decode(memberIdBase58).toList()

    @Ignore
    val conversationId: ID = Base58.decode(conversationIdBase58).toList()
}