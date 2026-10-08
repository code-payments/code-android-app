package com.flipcash.shared.chat

import com.flipcash.services.models.chat.ChatMessage
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Resolves the links in messages as they arrive, so a card is drawn with its answer on the frame
 * its message first appears rather than growing into it a round trip later.
 *
 * A card that changes size when its lookup lands moves every message above it, because the
 * transcript is anchored at the bottom. Asking before the write is what takes that jump away: by
 * the time the row reaches the transcript, the answer is already held.
 *
 * The lookups live in `:apps:flipcash:features:messenger`, which this module cannot see, so this is
 * the seam; `MessageLinkPrefetcher` on the far side is the implementation.
 *
 * [webLinks] lets outside https links be fetched too. Only a caller that knows the messages are in
 * the chat on screen, and that the viewer is a member of it, passes true. A web fetch is visible
 * to the linked host, so it must happen no earlier than the read receipt it stands beside. A feed
 * sync, a delivery or a push never passes it.
 */
interface MessageLinkPrefetch {

    /**
     * Starts resolving every link card in [messages] that has no answer held yet, and suspends
     * until they have all answered or [wait] has passed, whichever is first.
     *
     * The lookups outlive the wait. A link slower than [wait] still lands in the store, and its
     * card draws the answer from there; only the message's write stops waiting for it. A [wait]
     * of zero starts the lookups and returns at once.
     *
     * Never throws: a lookup that fails leaves the card to ask for itself when drawn.
     */
    suspend fun prefetch(messages: List<ChatMessage>, wait: Duration = Duration.ZERO, webLinks: Boolean = false)

    /** Does nothing. What a delegate built without a prefetcher -- a unit test -- is given. */
    object None : MessageLinkPrefetch {
        override suspend fun prefetch(messages: List<ChatMessage>, wait: Duration, webLinks: Boolean) = Unit
    }

    companion object {
        /**
         * How long a live message's write waits for its links. Long enough for one lookup on a
         * healthy connection; short enough that a slow one costs the message a beat rather than
         * holding it back.
         */
        val LIVE_WAIT: Duration = 1.seconds
    }
}
