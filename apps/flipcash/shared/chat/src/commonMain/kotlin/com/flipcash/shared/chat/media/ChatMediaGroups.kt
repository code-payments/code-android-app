package com.flipcash.shared.chat.media

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Which photos draw as one card stack. Nothing on a message says which photos were sent together,
 * so the set is inferred from what a send looks like on the wire: one photo per message, posted
 * back to back, so the ids run consecutively; the reply on the first and the caption on the last.
 * Shared with iOS through `chat_media_groups.json`.
 */
object ChatMediaGroups {

    /** The longest gap between two photos of one stack, inclusive. */
    val WINDOW: Duration = 2.seconds

    /** The facts about one message the rule reads. */
    data class Candidate(
        val messageId: Long,
        val senderKey: Any?,
        val isPhoto: Boolean,
        val timestamp: Instant,
        val hasCaption: Boolean,
        val isReply: Boolean,
    )

    /**
     * Whether [newer] continues the stack [older] is in, where [newer] is the next message after
     * [older] in the chat. Each term only looks at the pair, so the stacks are the maximal runs this
     * holds across, which is what grouping greedily from the oldest message gives.
     */
    fun joins(older: Candidate, newer: Candidate): Boolean =
        older.isPhoto && newer.isPhoto &&
            older.senderKey == newer.senderKey &&
            newer.messageId == older.messageId + 1 &&
            newer.timestamp - older.timestamp in Duration.ZERO..WINDOW &&
            // A caption ends a set and a reply starts one.
            !older.hasCaption && !newer.isReply

    /** The stacks in [messages], given oldest first: the ids of each run of two or more. */
    fun stacks(messages: List<Candidate>): List<List<Long>> {
        val stacks = ArrayList<List<Long>>()
        var run = ArrayList<Long>()
        messages.forEachIndexed { i, message ->
            if (i > 0 && !joins(messages[i - 1], message)) {
                if (run.size > 1) stacks += run
                run = ArrayList()
            }
            run += message.messageId
        }
        if (run.size > 1) stacks += run
        return stacks
    }
}
