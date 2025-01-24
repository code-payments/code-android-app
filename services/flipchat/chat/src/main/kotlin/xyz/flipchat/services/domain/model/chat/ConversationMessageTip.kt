package xyz.flipchat.services.domain.model.chat

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import com.getcode.model.ID
import com.getcode.model.KinAmount
import com.getcode.vendor.Base58
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "tips",
    indices = [
        Index(value = ["messageIdBase58"]),
    ],
)
data class ConversationMessageTip(
    @PrimaryKey
    val idBase58: String,
    val messageIdBase58: String,
    val amount: Long,
    val tipperIdBase58: String,
) {
    @Ignore
    val id: ID = Base58.decode(idBase58).toList()

    @Ignore
    val messageId: ID = Base58.decode(messageIdBase58).toList()

    @Ignore
    val tipperId: ID = Base58.decode(tipperIdBase58).toList()

    @Ignore
    val kin: KinAmount = KinAmount.fromQuarks(amount)
}
