package com.getcode.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.getcode.manager.SessionManager
import com.getcode.utils.serializer.ConversationMessageContentSerializer
import com.getcode.vendor.Base58
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
@Entity(tableName = "conversations")
data class Conversation(
    @PrimaryKey
    val idBase58: String,
    @ColumnInfo(defaultValue = "Tip Chat")
    val title: String,
    val hasRevealedIdentity: Boolean,
    val user: String?,
    val userImage: String?,
    val lastActivity: Long?,
) {
    @Ignore
    val id: ID = Base58.decode(idBase58).toList()

    override fun toString(): String {
        return """
            {
            id:${idBase58},
            title:$title,
            hasRevealedIdentity:$hasRevealedIdentity,
            user:$user,
            }
        """.trimIndent()
    }
}

@Serializable
@Entity(tableName = "messages")
data class ConversationMessage(
    @PrimaryKey
    val idBase58: String,
    val cursorBase58: String,
    val conversationIdBase58: String,
    val dateMillis: Long,
    @Serializable(with = ConversationMessageContentSerializer::class)
    val content: ConversationMessageContent,
    @ColumnInfo(defaultValue = "Unknown")
    val status: MessageStatus,
) {
    @Ignore
    val id: ID = Base58.decode(idBase58).toList()
    @Ignore
    val conversationId: ID = Base58.decode(conversationIdBase58).toList()
    @Ignore
    val cursor: Cursor = Base58.decode(cursorBase58).toList()
}

data class ConversationWithMessages(
    @Embedded val user: Conversation,
    @Relation(
        parentColumn = "idBase58",
        entityColumn = "conversationIdBase58"
    )
    val messages: List<ConversationMessage>,
)

@Entity(tableName = "messages_remote_keys")
data class ConversationMessageRemoteKey(
    @PrimaryKey
    val messageIdBase58: String,
    val prevCursorBase58: String?,
    val nextCursorBase58: String?
) {
    @Ignore
    val messageId: ID = Base58.decode(messageIdBase58).toList()
    @Ignore
    val prevCursor: Cursor? = prevCursorBase58?.let { Base58.decode(it).toList() }
    @Ignore
    val nextCursor: Cursor? = nextCursorBase58?.let { Base58.decode(it).toList() }
}

@Entity(tableName = "conversation_intent_id_mapping")
data class ConversationIntentIdReference(
    @PrimaryKey
    val conversationIdBase58: String,
    val intentIdBase58: String,
) {
    @Ignore
    val conversationId: ID = Base58.decode(conversationIdBase58).toList()
    @Ignore
    val intentId: ID = Base58.decode(intentIdBase58).toList()
}

sealed interface ConversationMessageContent {
    val kind: Int
    val isFromSelf: Boolean

    @Serializable
    data class Text(
        val message: String,
        val status: MessageStatus,
        override val isFromSelf: Boolean
    ) : ConversationMessageContent {
        override val kind: Int = 0
    }

    @Serializable
    data class TipMessage(override val isFromSelf: Boolean = false, val kinAmount: KinAmount) : ConversationMessageContent {
        override val kind: Int = 1
    }
    @Serializable
    data class ThanksSent(override val isFromSelf: Boolean = true) : ConversationMessageContent {
        override val kind: Int = 2
    }
    @Serializable
    data class ThanksReceived(override val isFromSelf: Boolean = false) : ConversationMessageContent {
        override val kind: Int = 3
    }
    @Serializable
    data class IdentityRevealed(override val isFromSelf: Boolean = true) : ConversationMessageContent {
        override val kind: Int = 4
    }
    @Serializable
    data class IdentityRevealedToYou(override val isFromSelf: Boolean = false) : ConversationMessageContent {
        override val kind: Int = 5
    }

    fun serialize(): String {
        val kind = kind
        val payload = when (this) {
            is IdentityRevealed,
            is IdentityRevealedToYou,
            is ThanksReceived,
            is ThanksSent ->  "$kind|${javaClass.simpleName}"
            is TipMessage -> {
                "$kind|${Json.encodeToString(this)}"
            }
            is Text -> "$kind|${Json.encodeToString(this)}"
        }

        return payload
    }

    companion object {
        fun deserialize(string: String): ConversationMessageContent {
            val (kind, data) = string.split("|")
            return when (kind.toInt()) {
                0 -> Json.decodeFromString<Text>(data)
                1 -> Json.decodeFromString<TipMessage>(data)
                2 -> ThanksSent()
                3 -> ThanksReceived()
                4 -> IdentityRevealed()
                5 -> IdentityRevealedToYou()
                else -> throw IllegalArgumentException()
            }
        }
    }
}

enum class MessageStatus {
    Incoming, Sent, Delivered, Read, Unknown;

    fun isOutgoing() = when (this) {
        Incoming -> false
        Sent,
        Delivered,
        Read -> true
        Unknown -> false
    }
    fun isValid() = this != Unknown
}