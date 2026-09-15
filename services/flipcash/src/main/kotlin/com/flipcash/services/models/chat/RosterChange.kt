package com.flipcash.services.models.chat

import com.getcode.opencode.model.core.ID

/**
 * A single change to a chat's roster: someone joined, or someone left.
 *
 * Roster changes ride the event stream as a convergent overlay, **outside** the gap-detected
 * event log that [ChatEvent] belongs to. There is no sequence to fill a gap against — each
 * change carries the roster's state after it, and a client applies one only when
 * [rosterSummary]'s version is greater than the version it already holds, dropping anything
 * older or equal. Delivery order therefore does not matter, and a missed change is reconciled
 * by refetching the roster rather than by a delta.
 */
sealed interface RosterChange {
    /** The chat's roster summary *after* this change. Decides whether the change applies. */
    val rosterSummary: RosterSummary

    /**
     * [member] joined the chat, with their profile already hydrated so the cached member list
     * can be updated without a refetch.
     *
     * [metadata] is set only when [member] is the recipient — that is, when *you* are the one
     * who joined — and is the full chat snapshot to insert into the chat list. It is null for
     * every other member's join.
     */
    data class MemberJoined(
        val member: ChatMember,
        val metadata: ChatMetadata?,
        override val rosterSummary: RosterSummary,
    ) : RosterChange

    /**
     * [userId] left the chat. When [userId] is the recipient, the recipient is no longer a
     * member and the chat should be removed from their list.
     */
    data class MemberLeft(
        val userId: ID,
        override val rosterSummary: RosterSummary,
    ) : RosterChange
}
