package com.flipcash.services.models

import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.solana.keys.PublicKey
import kotlin.time.Instant
import kotlinx.serialization.Serializable

/**
 *  Notification that is displayed in an activity feed
 *
 *  @param id The ID of the notification, which is guaranteed to be consistent for grouped
 *  events. Updates to a notification with the same ID should result in re-ordering
 *  within the activity feed using the latest content.
 *  @param text The localized title text for the notification
 *  @param amount If a payment applies, the amount that was paid
 *  @param timestamp The timestamp of this notification
 *  @param state The state of this notification
 *  @param metadata Additional metadata for this notification specific to the notification
 */
data class ActivityFeedNotification(
    val id: ID,
    val text: String,
    val amount: LocalFiat?,
    val timestamp: Instant,
    val state: NotificationState,
    val metadata: NotificationMetadata?,
    val textSubstitutions: List<Substitution> = emptyList(),
)

/**
 * Determines the mutability of a notification, and whether client should attempt to refetch state.
 */
enum class NotificationState {
    /**
     * ¯\_(ツ)_/¯
     */
    UNKNOWN,

    /**
     * Notification state will change based on some app action in the future
     */
    PENDING,

    /**
     * Notification state will not change
     */
    COMPLETED
}

@Serializable
sealed interface NotificationMetadata {
    @Serializable
    data object Unknown : NotificationMetadata

    @Serializable
    data class DirectlySentCrypto(
        val phoneNumber: String? = null,
        val userId: ID? = null,
    ) : NotificationMetadata

    /**
     * @param creator The vault of the gift card account that was created for the cash link
     * @param canCancel Whether the cancel action can be initiated by the user
     */
    @Serializable
    data class IndirectlySentCrypto(
        val creator: PublicKey,
        val canCancel: Boolean,
    ) : NotificationMetadata

    @Serializable
    data object DepositedCrypto : NotificationMetadata

    @Serializable
    data class ReceivedCrypto(
        val phoneNumber: String? = null,
        val userId: ID? = null,
    ) : NotificationMetadata

    /**
     * @param swapMetadata When the withdrawal was executed as a swap, the metadata for that swap.
     * Null for a plain withdrawal. (The server's deprecated per-half `swap_state` is superseded by
     * this.)
     */
    @Serializable
    data class WithdrewCrypto(
        val swapMetadata: SwappedCryptoMetadata? = null,
    ) : NotificationMetadata

    // Superseded by [SwappedCrypto] (which models both halves of a swap as one event). Retained so
    // historical bought/sold notifications still map.
    @Serializable
    data object BoughtToken: NotificationMetadata
    @Serializable
    data object SoldToken: NotificationMetadata

    /**
     * A swap between two mints, modeled as a single event. Supersedes [BoughtToken]/[SoldToken].
     */
    @Serializable
    data class SwappedCrypto(
        val swap: SwappedCryptoMetadata,
    ) : NotificationMetadata
}

/**
 * The state of a swap as a whole.
 */
enum class SwapState {
    UNKNOWN,
    PENDING,
    SUCCEEDED,
    FAILED,
    NONE,
}

/**
 * Details of a crypto swap between two mints.
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