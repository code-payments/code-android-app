package com.flipcash.app.core.feed

import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.solana.keys.PublicKey
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class ActivityFeedMessage(
    val id: ID,
    val text: String,
    val amount: LocalFiat?,
    val timestamp: Instant,
    val state: MessageState,
    val metadata: MessageMetadata?
)

enum class MessageState {
    UNKNOWN,
    PENDING,
    COMPLETED;

    companion object {
        fun from(named: String) = try {
            valueOf(named.uppercase())
        } catch (e: IllegalArgumentException) {
            UNKNOWN
        }
    }
}

@Serializable
sealed interface MessageMetadata {
    @Serializable
    data object Unknown : MessageMetadata

    @Serializable
    data object WelcomeBonus : MessageMetadata

    @Serializable
    data object GaveUsdc : MessageMetadata

    @Serializable
    data class SentUsdc(
        val creator: PublicKey,
        val canCancel: Boolean,
    ) : MessageMetadata

    @Serializable
    data object ReceivedUsdc : MessageMetadata

    @Serializable
    data object WithdrewUsdc : MessageMetadata

    @Serializable
    data object DepositedUsdc : MessageMetadata

    companion object {
        fun from(named: String?): MessageMetadata? {
            named ?: return null
            return try {
                Json.decodeFromString<MessageMetadata>(named)
            } catch (e: IllegalArgumentException) {
                Unknown
            }
        }
    }
}