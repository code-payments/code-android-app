package com.flipcash.app.core.feed

import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.solana.keys.PublicKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Instant

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
    val metadata: MessageMetadata?,
    // Ordered substitutions for [text]'s indexed placeholders ({0}, {1}, …). Persisted so the
    // title can be resolved live at display time (see TransactionItemMapper), keeping it consistent
    // with the live-resolved counterparty avatar.
    val textSubstitutions: List<MessageSubstitution> = emptyList(),
) {
    val isTransaction: Boolean
        get() = amount != null

    val canCancel: Boolean
        get() {
            metadata ?: return false
            val metadata =
                (metadata as? MessageMetadata.IndirectlySentCrypto) ?: return false
            return metadata.canCancel
        }
}

/**
 * A substitution for an indexed placeholder in [ActivityFeedMessage.text]. The core-domain,
 * persisted mirror of `com.flipcash.services.models.Substitution` (mapped at the persistence
 * boundary, like [MessageMetadata]). [fallback] is the server-provided name; the client prefers
 * a live-resolved name for [UserId] where available.
 */
@Serializable
sealed interface MessageSubstitution {
    val fallback: String

    @Serializable
    data class Phone(
        override val fallback: String,
        val phoneNumber: String,
    ) : MessageSubstitution

    @Serializable
    data class UserId(
        override val fallback: String,
        val userId: ID,
    ) : MessageSubstitution

    companion object {
        /** Deserializes the persisted JSON list; empty (never throws) on null/garbage. */
        fun listFrom(json: String?): List<MessageSubstitution> {
            json ?: return emptyList()
            return try {
                Json.decodeFromString<List<MessageSubstitution>>(json)
            } catch (e: Exception) {
                emptyList()
            }
        }
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
    data class DirectlySentCrypto(
        val phoneNumber: String? = null,
        val userId: ID? = null,
    ) : MessageMetadata

    @Serializable
    data class IndirectlySentCrypto(
        val creator: PublicKey,
        val canCancel: Boolean,
    ) : MessageMetadata

    @Serializable
    data class ReceivedCrypto(
        val phoneNumber: String? = null,
        val userId: ID? = null,
    ) : MessageMetadata

    /**
     * @param swapMetadata When the withdrawal was executed as a swap, the metadata for that swap;
     * null for a plain withdrawal.
     */
    @Serializable
    data class WithdrewCrypto(
        val swapMetadata: SwappedCryptoMetadata? = null,
    ) : MessageMetadata

    @Serializable
    data object DepositedCrypto : MessageMetadata

    // Superseded by [SwappedCrypto]; retained so historical bought/sold messages still deserialize.
    @Serializable
    data object BoughtToken: MessageMetadata

    @Serializable
    data object SoldToken: MessageMetadata

    /**
     * A swap between two mints, modeled as a single event. Supersedes [BoughtToken]/[SoldToken].
     */
    @Serializable
    data class SwappedCrypto(
        val swap: SwappedCryptoMetadata,
    ): MessageMetadata

    @Serializable
    data class PaidCrypto(
        val poolId: ID,
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

/**
 * The state of a swap as a whole. Persisted mirror of `com.flipcash.services.models.SwapState`.
 */
enum class SwapState {
    UNKNOWN,
    PENDING,
    SUCCEEDED,
    FAILED,
    NONE,
}

/**
 * Persisted mirror of `com.flipcash.services.models.SwappedCryptoMetadata`.
 *
 * @param from The amount the user gave up in the source mint.
 * @param toMint The destination mint. Always known, even while the swap is pending.
 * @param toAmount The amount received in the destination mint. Null until the swap has executed.
 * @param fee The fee charged for the swap, known upfront regardless of swap state.
 * @param swapState The state of the swap as a whole.
 */
@Serializable
data class SwappedCryptoMetadata(
    val from: LocalFiat,
    val toMint: PublicKey,
    val toAmount: LocalFiat?,
    val fee: Fiat,
    val swapState: SwapState,
)