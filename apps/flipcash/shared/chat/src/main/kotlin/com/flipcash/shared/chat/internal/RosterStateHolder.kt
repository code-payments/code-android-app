package com.flipcash.shared.chat.internal

import com.flipcash.app.persistence.sources.ChatMemberDataSource
import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.RosterChange
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides which roster changes apply to a chat, and repairs the roster when one cannot.
 *
 * The state it arbitrates is `chat_metadata.roster_version`, not a field of its own — a version
 * held in memory would diverge from the row the moment a feed sync wrote a newer one. What this
 * class owns is the rule, which is deliberately not the rule the event log follows:
 *
 * - **Newer wins, equal loses.** Every change carries the roster's summary *after* it, so a change
 *   at or below the stored version has nothing to add. Re-delivery is normal on an at-least-once
 *   stream.
 * - **A skipped version is a refetch, not an apply.** There is no delta to fill the hole with, and
 *   applying the change anyway would write a member count that does not describe the roster the
 *   device holds. [ChatController.getChat] returns the current one instead.
 *
 * Kept out of [com.flipcash.shared.chat.EventSequenceTracker], whose contract is that
 * sequence N+1 follows N and a hole is fetched. Folding a rule with the opposite repair into it
 * would make that invariant conditional on the kind of update being handled.
 */
@Singleton
class RosterStateHolder @Inject constructor(
    private val chatController: ChatController,
    private val metadataDataSource: ChatMetadataDataSource,
    private val memberDataSource: ChatMemberDataSource,
) {

    /**
     * Applies [changes] to [chatId]'s cached roster, oldest version first.
     *
     * Sorting matters: the changes arrive in a batch with no ordering guarantee, and out of order
     * the older of two consecutive versions reads as a gap and costs a refetch for something the
     * device is about to be told anyway.
     */
    suspend fun apply(chatId: ChatId, changes: List<RosterChange>) {
        for (change in changes.sortedBy { it.rosterSummary.version }) {
            val stored = metadataDataSource.getRosterVersion(chatId)
            val incoming = change.rosterSummary.version

            when {
                incoming <= stored -> continue
                incoming > stored + 1 -> refetch(chatId, stored, incoming)
                else -> applyChange(chatId, change)
            }
        }
    }

    private suspend fun applyChange(chatId: ChatId, change: RosterChange) {
        when (change) {
            is RosterChange.MemberJoined -> memberDataSource.upsert(chatId, listOf(change.member))
            is RosterChange.MemberLeft -> memberDataSource.deleteMember(chatId, change.userId)
        }
        metadataDataSource.updateRoster(
            chatId = chatId,
            memberCount = change.rosterSummary.memberCount,
            rosterVersion = change.rosterSummary.version,
        )
    }

    /**
     * Replaces the cached roster with the server's current one.
     *
     * Members are merged rather than replaced. A group's member list is a page of a roster whose
     * true size is `member_count`, so deleting everything absent from one response would drop
     * members the device legitimately holds — including the senders a transcript needs to name.
     * Departures come through [RosterChange.MemberLeft]; this is only here to get the count and
     * the version back in step with the server.
     */
    private suspend fun refetch(chatId: ChatId, stored: Long, incoming: Long) {
        trace(
            tag = TAG,
            message = "Roster version gap on $chatId: stored $stored, incoming $incoming",
            type = TraceType.Silent,
        )
        val metadata = chatController.getChat(chatId).getOrElse {
            // Leaving the stored version alone is what makes this retryable: the next change on
            // this chat still reads as a gap, and tries again.
            trace(tag = TAG, message = "Roster refetch failed for $chatId", type = TraceType.Error)
            return
        }
        metadataDataSource.upsert(metadata)
        memberDataSource.upsert(chatId, metadata.members)
    }

    private companion object {
        const val TAG = "RosterStateHolder"
    }
}
