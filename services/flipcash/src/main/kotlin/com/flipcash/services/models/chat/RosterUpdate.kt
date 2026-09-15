package com.flipcash.services.models.chat

import com.getcode.opencode.model.core.ID

/**
 * A best-effort, real-time change to a chat's roster — a member joining (e.g. via
 * `Chat.JoinChat`, or as the first member via `Chat.StartChat`) or leaving (e.g. via
 * `Chat.LeaveChat`). Delivered to the chat's members, including the affected user's other
 * devices.
 *
 * Roster changes are a convergent overlay, so they ride the event stream outside the
 * gap-detected event log. Apply by [rosterSummary]'s version as described on [RosterSummary]:
 * a greater version than the one held means the cached member list is stale and this update
 * should be applied; a lesser-or-equal version should be dropped, so delivery order doesn't
 * matter. A missed update is not caught up via `GetDelta` but reconciled by refetching the
 * roster when a client observes a version it cannot reconcile.
 */
sealed interface RosterUpdate {
    /** The chat's roster summary after this change, applied by version as described above. */
    val rosterSummary: RosterSummary

    /**
     * A member joined the chat.
     *
     * When [metadata] is set, [member] IS the recipient: the recipient has just become a
     * member of the chat and should insert [metadata] into their chat list. Otherwise this is
     * another member joining a chat the recipient is already in.
     */
    data class MemberJoined(
        override val rosterSummary: RosterSummary,
        // The member that joined, with their profile hydrated, so a client can update its
        // cached member list without a refetch.
        val member: ChatMember,
        // Set only when [member] is the recipient; null for every other member of the chat.
        // The recipient inserts the chat from this snapshot, then applies the enclosing
        // [rosterSummary] by version like any other roster update.
        val metadata: ChatMetadata?,
    ) : RosterUpdate

    /**
     * A member left the chat.
     *
     * When [userId] is the recipient's own id, the recipient is no longer a member of the chat
     * and should remove it from their chat list.
     */
    data class MemberLeft(
        override val rosterSummary: RosterSummary,
        val userId: ID,
    ) : RosterUpdate
}
