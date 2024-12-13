package xyz.flipchat.services.domain.model.chat

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.getcode.model.ID
import com.getcode.model.chat.MessageContent
import com.getcode.vendor.Base58
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["conversationIdBase58"]), // For filtering by conversation ID
        Index(value = ["senderIdBase58"]),       // For joining on sender ID
        Index(value = ["dateMillis"])           // For ordering by date
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
    @ColumnInfo(defaultValue = "1")
    val type: Int,
    @ColumnInfo(defaultValue = "")
    val content: String,
) {
    @Ignore
    val id: ID = Base58.decode(idBase58).toList()

    @Ignore
    val conversationId: ID = Base58.decode(conversationIdBase58).toList()

    @Ignore
    val senderId: ID = Base58.decode(senderIdBase58).toList()

    @Ignore
    val isDeleted: Boolean = deleted == true
}

data class ConversationMessageWithMember(
    @Embedded val message: ConversationMessage,
    @Relation(
        parentColumn = "senderIdBase58",
        entityColumn = "memberIdBase58",
        entity = ConversationMember::class,
    )
    val member: ConversationMember?
)

data class ConversationMessageWithMemberAndContent(
    @Embedded val message: ConversationMessage,
    val member: ConversationMember?,
    val content: MessageContent
)