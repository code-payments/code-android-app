package com.getcode.oct24.domain.model.chat

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.getcode.model.ID
import com.getcode.model.chat.MessageContent
import com.getcode.vendor.Base58
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "messages")
data class ConversationMessage(
    @PrimaryKey
    val idBase58: String,
    val conversationIdBase58: String,
    @ColumnInfo(defaultValue = "")
    val senderIdBase58: String,
    val dateMillis: Long,
    private val deleted: Boolean?,
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

data class ConversationMessageWithContentAndMember(
    @Embedded val message: ConversationMessage,
    @Relation(
        parentColumn = "idBase58",
        entityColumn = "messageIdBase58",
        entity = ConversationMessageContent::class,
        projection = ["content"]
    )
    val contents: List<MessageContent>,
    @Relation(
        parentColumn = "senderIdBase58",
        entityColumn = "memberIdBase58",
        entity = ConversationMember::class
    )
    val member: ConversationMember?
)