package com.flipcash.app.core.feed

import com.flipcash.app.core.pools.PoolResolution
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.solana.keys.PublicKey
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class ActivityFeedMessageWithToken(
    val message: ActivityFeedMessage,
    val token: Token?
)

data class ActivityFeedMessage(
    val id: ID,
    val text: String,
    val amount: LocalFiat?,
    val timestamp: Instant,
    val state: MessageState,
    val metadata: MessageMetadata?
) {
    val isTransaction: Boolean
        get() = amount != null

    val canCancel: Boolean
        get() {
            metadata ?: return false
            val metadata =
                (metadata as? MessageMetadata.SentCrypto) ?: return false
            return metadata.canCancel
        }
}

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
    data object GaveCrypto : MessageMetadata

    @Serializable
    data class SentCrypto(
        val creator: PublicKey,
        val canCancel: Boolean,
    ) : MessageMetadata

    @Serializable
    data object ReceivedCrypto : MessageMetadata

    @Serializable
    data object WithdrewCrypto : MessageMetadata

    @Serializable
    data object DepositedCrypto : MessageMetadata

    @Serializable
    data class PaidCrypto(
        val poolId: ID,
    ): MessageMetadata

    @Serializable
    data class DistributedCrypto(
        val poolId: ID,
        val outcome: PoolResolution,
    ): MessageMetadata

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