package xyz.flipchat.services.domain.model.chat

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import com.getcode.model.ID
import com.getcode.vendor.Base58
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "reactions",
    indices = [
        Index(value = ["messageIdBase58"]),
    ],
)
data class ConversationMessageReaction(
    @PrimaryKey
    val idBase58: String,
    val messageIdBase58: String,
    val senderIdBase58: String,
    val emoji: String,
) {
    @Ignore
    val id: ID = Base58.decode(idBase58).toList()

    @Ignore
    val messageId: ID = Base58.decode(messageIdBase58).toList()

    @Ignore
    val senderId: ID = Base58.decode(senderIdBase58).toList()
}
