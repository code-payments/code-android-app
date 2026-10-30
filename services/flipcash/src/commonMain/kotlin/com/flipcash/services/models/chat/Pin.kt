package com.flipcash.services.models.chat

import com.flipcash.services.models.PagingToken
import com.getcode.opencode.model.core.ID
import kotlin.time.Instant

/**
 * A message pinned in a chat. Mirrors `chat.v1.Pin`.
 *
 * [message] is always set and may be redacted depending on the view mode of the read. A message
 * deleted after it was pinned stays pinned as a tombstone; clients should not show it.
 */
data class Pin(
    val messageId: Long,
    val pinnedBy: ID,
    val pinnedAt: Instant,
    val message: ChatMessage,
)

/**
 * A chat's pins, described without listing them. Mirrors `chat.v1.PinSummary`.
 */
class PinSummary(
    // The most recently pinned message; null when nothing is pinned.
    val latest: Pin? = null,
    // Number of pinned messages. The cap is server-defined and must not be hardcoded.
    val count: Int = 0,
    // Advanced by exactly one on every pin or unpin. Compared like RosterSummary.version:
    // apply the greater value, drop the rest.
    val version: Long = 0,
    // `latest` as the serialized `chat.v1.Pin`, set when this summary was read off the wire or
    // out of storage. It is what persistence stores: the pinned message may not be in the local
    // messages table, so the whole pin is kept as the server sent it. Derived from [latest], so
    // it takes no part in equality; a summary built by hand has none and stores no latest pin.
    val latestEncoded: ByteArray? = null,
) {
    fun copy(
        latest: Pin? = this.latest,
        count: Int = this.count,
        version: Long = this.version,
    ): PinSummary = PinSummary(
        latest = latest,
        count = count,
        version = version,
        latestEncoded = latestEncoded.takeIf { latest === this.latest },
    )

    override fun equals(other: Any?): Boolean =
        this === other || (other is PinSummary &&
            latest == other.latest && count == other.count && version == other.version)

    override fun hashCode(): Int = 31 * (31 * count + version.hashCode()) + (latest?.hashCode() ?: 0)

    override fun toString(): String = "PinSummary(latest=$latest, count=$count, version=$version)"

    companion object
}

/** The pin that a `MetadataUpdate.PinsChanged` carries: a message pinned or unpinned. */
sealed interface PinChange {
    data class Pinned(val pin: Pin) : PinChange
    data class Unpinned(val messageId: Long) : PinChange
}

/** One page of `Chat.GetPinnedMessages`, most recently pinned first. */
data class PinsPage(
    val pins: List<Pin>,
    val pinSummary: PinSummary,
    val pagingToken: PagingToken?,
    val hasMore: Boolean,
)
