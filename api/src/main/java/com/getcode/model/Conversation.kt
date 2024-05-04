package com.getcode.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.getcode.manager.SessionManager
import com.getcode.network.repository.base58
import com.getcode.utils.serializer.ConversationMessageContentSerializer
import com.getcode.utils.serializer.RateAsStringSerializer
import com.getcode.vendor.Base58
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule

@Serializable
@Entity(tableName = "conversations")
data class Conversation(
    @PrimaryKey
    val idBase58: String,
    val messageIdBase58: String,
    val cursorBase58: String,
    val tipAmount: KinAmount,
    val createdByUser: Boolean, // if this conversation was created as a result of the user messaging the tipper.,
    val hasRevealedIdentity: Boolean,
    val user: String?,
    val userImage: String?,
    val lastActivity: Long?,
) {
    @Ignore
    val id: ID = Base58.decode(idBase58).toList()
    @Ignore
    val messageId: ID = Base58.decode(messageIdBase58).toList()
    @Ignore
    val cursor: Cursor = Base58.decode(cursorBase58).toList()

    override fun toString(): String {
        return """
            {
            id:${idBase58},
            messageId:${messageIdBase58},
            tipAmount:$tipAmount,
            createByUser:$createdByUser,
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

sealed interface ConversationMessageContent {
    val kind: Int

    @Serializable
    data class Text(
        val message: String,
        val status: MessageStatus,
        val from: String,
    ) : ConversationMessageContent {
        override val kind: Int = 0
        val isFromSelf: Boolean
            get() = SessionManager.getOrganizer()?.primaryVault?.let { Base58.encode(it.byteArray) } == from
    }

    @Serializable
    data object TipMessage : ConversationMessageContent {
        override val kind: Int = 1
    }
    @Serializable
    data object ThanksSent : ConversationMessageContent {
        override val kind: Int = 2
    }
    @Serializable
    data object ThanksReceived : ConversationMessageContent {
        override val kind: Int = 3
    }
    @Serializable
    data object IdentityRevealed : ConversationMessageContent {
        override val kind: Int = 4
    }
    @Serializable
    data object IdentityRevealedToYou : ConversationMessageContent {
        override val kind: Int = 5
    }

    fun serialize(): String {
        val kind = kind
        val payload = when (this) {
            IdentityRevealed,
            IdentityRevealedToYou,
            ThanksReceived,
            ThanksSent,
            TipMessage -> {
                "$kind|${javaClass.simpleName}"
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
                1 -> TipMessage
                2 -> ThanksSent
                3 -> ThanksReceived
                4 -> IdentityRevealed
                5 -> IdentityRevealedToYou
                else -> throw IllegalArgumentException()
            }
        }
    }
}

enum class MessageStatus {
    Incoming, Sent, Delivered, Read
}