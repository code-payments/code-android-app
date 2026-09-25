package com.flipcash.shared.chat.reactions

import kotlin.time.Instant

/**
 * One message's reactions: the server state last accepted per emoji, merged with the user's
 * pending taps.
 *
 * This is `gen_reactions.py`'s `Model`, and `reactions.json`'s `merge` vectors hold it to that.
 * Server state (summary, stream update, successful response) is accepted only when its version is
 * strictly greater than the one held for that emoji. The displayed self state is the pending tap
 * when there is one, otherwise the confirmed one, so the stream never overwrites a pending tap.
 *
 * Only [confirmed] would be encoded on persistence: pending taps and in-flight calls do not
 * survive a relaunch.
 */
class ReactionState {

    /** The last server state accepted for one emoji. */
    data class Confirmed(
        val count: Long,
        val selfReacted: Boolean,
        val version: Long,
        /**
         * When the user reacted with this emoji, if they have; orders the strip and takes no part
         * in the merge.
         */
        val selfReactedAt: Instant?,
    )

    /** One emoji as a reaction summary lists it. */
    data class SummaryEntry(
        val emoji: String,
        val count: Long,
        val selfReacted: Boolean,
        val version: Long,
        val selfReactedAt: Instant?,
    )

    /**
     * The accepted server state per emoji; a count of 0 is a tombstone that keeps the emptied
     * emoji's version so a late, older add is still rejected.
     */
    private val confirmedByEmoji: MutableMap<String, Confirmed> = mutableMapOf()

    /** The self state the user last tapped to, per emoji, until the server settles it. */
    private val desired: MutableMap<String, Boolean> = mutableMapOf()

    /** Emoji with an add or remove call on the wire. */
    private val inFlightEmoji: MutableSet<String> = mutableSetOf()

    /** When the user tapped each emoji whose [desired] state is on. */
    private val desiredAt: MutableMap<String, Instant> = mutableMapOf()

    val confirmed: Map<String, Confirmed> get() = confirmedByEmoji
    val inFlight: Set<String> get() = inFlightEmoji

    // region Server state

    /**
     * Accepts a reaction summary, which lists only non-empty emoji: a held emoji it omits has
     * emptied, unless a tap on it is pending.
     */
    fun applySummary(reactions: List<SummaryEntry>) {
        val present = reactions.map { it.emoji }.toSet()
        for (entry in reactions) {
            accept(
                entry.emoji,
                Confirmed(
                    count = entry.count,
                    selfReacted = entry.selfReacted,
                    version = entry.version,
                    selfReactedAt = entry.selfReactedAt,
                ),
            )
        }
        for ((emoji, held) in confirmedByEmoji.toMap()) {
            if (emoji !in present && desired[emoji] == null && held.count > 0) {
                confirmedByEmoji[emoji] = held.copy(count = 0, selfReacted = false, selfReactedAt = null)
            }
        }
    }

    /** Accepts one stream update; [count] is the emoji's new total, not a delta. */
    fun applyUpdate(
        emoji: String,
        actorIsSelf: Boolean,
        added: Boolean,
        count: Long,
        version: Long,
        reactedAt: Instant?,
    ) {
        val selfReacted: Boolean
        val selfReactedAt: Instant?
        if (actorIsSelf) {
            selfReacted = added
            selfReactedAt = if (added) reactedAt else null
        } else {
            selfReacted = confirmedByEmoji[emoji]?.selfReacted ?: false
            selfReactedAt = confirmedByEmoji[emoji]?.selfReactedAt
        }
        accept(emoji, Confirmed(count = count, selfReacted = selfReacted, version = version, selfReactedAt = selfReactedAt))
    }

    /**
     * Accepts every emoji [other] has confirmed, each by version as any server state is, and
     * ignores its pending taps.
     */
    fun merge(other: ReactionState) {
        for ((emoji, state) in other.confirmedByEmoji) {
            accept(emoji, state)
        }
    }

    /** The confirmed non-empty emoji, as a summary would list them. */
    val summaryEntries: List<SummaryEntry>
        get() = confirmedByEmoji.mapNotNull { (emoji, state) ->
            if (state.count <= 0) return@mapNotNull null
            SummaryEntry(
                emoji = emoji,
                count = state.count,
                selfReacted = state.selfReacted,
                version = state.version,
                selfReactedAt = state.selfReactedAt,
            )
        }

    /** Whether no tap is pending and no call is on the wire. */
    val isSettled: Boolean get() = desired.isEmpty() && inFlightEmoji.isEmpty()

    // endregion

    // region User actions

    /**
     * Flips the displayed self state of [emoji] and returns the call to send, or null when a call
     * for it is already on the wire and the response will send any follow-up.
     */
    fun tap(emoji: String, at: Instant): ReactionCall? {
        val on = !displayedSelf(emoji)
        desired[emoji] = on
        if (on) desiredAt[emoji] = at else desiredAt.remove(emoji)
        if (emoji in inFlightEmoji) return null
        return send(emoji)
    }

    /**
     * Settles the call in flight for [emoji], returning a coalesced follow-up call and the error
     * to show, if any; a response for an emoji with nothing in flight is ignored.
     */
    fun respond(emoji: String, result: ReactionResult): Pair<ReactionCall?, ReactionError?> {
        if (!inFlightEmoji.remove(emoji)) return null to null
        return when (result) {
            is ReactionResult.Ok -> {
                accept(
                    emoji,
                    Confirmed(
                        count = result.count,
                        selfReacted = result.selfReacted,
                        version = result.version,
                        selfReactedAt = result.selfReactedAt,
                    ),
                )
                val wanted = desired[emoji]
                if (wanted != null && wanted != (confirmedByEmoji[emoji]?.selfReacted ?: false)) {
                    send(emoji) to null
                } else {
                    clearDesired(emoji)
                    null to null
                }
            }

            is ReactionResult.Failed -> {
                clearDesired(emoji)
                null to result.failure.userError
            }
        }
    }

    // endregion

    // region Display

    /** The pills to show, in [ReactionOrdering]; an emoji whose displayed count is 0 has none. */
    val pills: List<ReactionPill>
        get() {
            val emoji = confirmedByEmoji.keys + desired.keys
            val pills = emoji.mapNotNull { emoji ->
                val (count, shown) = displayed(emoji)
                if (count <= 0) return@mapNotNull null
                ReactionPill(emoji = emoji, count = count, selfReacted = shown, pending = desired[emoji] != null)
            }
            return ReactionOrdering.sorted(pills)
        }

    /** The emoji the user is shown reacting with, each with when they reacted. */
    val selfReactions: List<SelfReaction>
        get() = (confirmedByEmoji.keys + desired.keys).mapNotNull { emoji ->
            if (!displayedSelf(emoji)) return@mapNotNull null
            val reactedAt = desiredAt[emoji] ?: confirmedByEmoji[emoji]?.selfReactedAt ?: Instant.DISTANT_PAST
            SelfReaction(emoji = emoji, reactedAt = reactedAt)
        }

    // endregion

    // region Private

    private fun accept(emoji: String, state: Confirmed) {
        val held = confirmedByEmoji[emoji]
        if (held != null && state.version <= held.version) return
        confirmedByEmoji[emoji] = state
    }

    private fun send(emoji: String): ReactionCall {
        inFlightEmoji.add(emoji)
        return ReactionCall(op = if (desired[emoji] == true) ReactionCall.Op.ADD else ReactionCall.Op.REMOVE, emoji = emoji)
    }

    private fun clearDesired(emoji: String) {
        desired.remove(emoji)
        desiredAt.remove(emoji)
    }

    private fun displayedSelf(emoji: String): Boolean = desired[emoji] ?: confirmedByEmoji[emoji]?.selfReacted ?: false

    /**
     * The displayed count, signed because an inconsistent confirmed state (self set on a count of
     * 0) must hide the pill rather than underflow.
     */
    private fun displayed(emoji: String): Pair<Long, Boolean> {
        val held = confirmedByEmoji[emoji]
        val shown = displayedSelf(emoji)
        val base = (held?.count ?: 0) - (if (held?.selfReacted == true) 1 else 0)
        return (base + if (shown) 1 else 0) to shown
    }

    // endregion
}
