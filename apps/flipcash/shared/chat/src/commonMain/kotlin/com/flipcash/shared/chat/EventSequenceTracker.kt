package com.flipcash.shared.chat

import com.flipcash.services.models.chat.ChatId
import kotlinx.coroutines.Job
import java.util.TreeSet

/**
 * Tracks per-chat event sequences to detect out-of-order delivery gaps.
 *
 * Only advances the "contiguous frontier" — the highest sequence number with
 * no gaps before it. Sequences that arrive ahead of the frontier are held in
 * a pending set until the gap is filled (either by a late-arriving event or
 * by a getDelta backfill).
 *
 * Session-scoped; initialized lazily from the DB cursor on first event per chat.
 */
internal class EventSequenceTracker {

    private val chatStates = mutableMapOf<ChatId, GapState>()

    private class GapState(
        var lastContiguous: Long,
        val pending: TreeSet<Long> = TreeSet(),
        var gapFillJob: Job? = null,
    )

    data class SequenceResult(
        val newContiguousSequence: Long,
        val hasGap: Boolean,
    )

    /**
     * Process incoming event sequences for a chat.
     *
     * @param chatId the chat these events belong to
     * @param dbCursor the persisted cursor (used to initialize on first call)
     * @param incomingSequences the sequence numbers from this batch of events
     * @return the new contiguous frontier and whether a gap remains
     */
    fun processSequences(
        chatId: ChatId,
        dbCursor: Long,
        incomingSequences: List<Long>,
    ): SequenceResult {
        val state = chatStates.getOrPut(chatId) { GapState(lastContiguous = dbCursor) }

        // If the DB cursor advanced externally (e.g. delta sync), catch up
        if (dbCursor > state.lastContiguous) {
            state.lastContiguous = dbCursor
            state.pending.headSet(dbCursor + 1).clear()
        }

        state.pending.addAll(incomingSequences)

        // Consume contiguous sequences from the frontier
        while (state.pending.isNotEmpty() && state.pending.first() == state.lastContiguous + 1) {
            state.lastContiguous = state.pending.pollFirst()!!
        }

        // Drop any sequences at or below the frontier (duplicates / late arrivals)
        if (state.pending.isNotEmpty()) {
            state.pending.headSet(state.lastContiguous + 1).clear()
        }

        return SequenceResult(
            newContiguousSequence = state.lastContiguous,
            hasGap = state.pending.isNotEmpty(),
        )
    }

    fun hasGap(chatId: ChatId): Boolean {
        return chatStates[chatId]?.pending?.isNotEmpty() == true
    }

    fun setGapFillJob(chatId: ChatId, job: Job) {
        val state = chatStates[chatId] ?: return
        state.gapFillJob?.cancel()
        state.gapFillJob = job
    }

    /** Reset tracker to a known-good sequence after an authoritative getDelta response. */
    fun resetTo(chatId: ChatId, sequence: Long) {
        val state = chatStates[chatId]
        if (state != null) {
            state.gapFillJob?.cancel()
            state.gapFillJob = null
            state.lastContiguous = sequence
            state.pending.clear()
        }
    }

    fun clear(chatId: ChatId) {
        chatStates.remove(chatId)?.gapFillJob?.cancel()
    }

    fun clearAll() {
        chatStates.values.forEach { it.gapFillJob?.cancel() }
        chatStates.clear()
    }
}
