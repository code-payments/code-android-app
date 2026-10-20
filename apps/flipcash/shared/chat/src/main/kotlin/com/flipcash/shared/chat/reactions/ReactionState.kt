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
        /** The emoji's total USD boost, in whole cents. */
        val boost: Long = 0,
        /** The part of [boost] the user paid. A reaction the user boosted can't be taken back. */
        val selfBoost: Long = 0,
    )

    /** One emoji as a reaction summary lists it. */
    data class SummaryEntry(
        val emoji: String,
        val count: Long,
        val selfReacted: Boolean,
        val version: Long,
        val selfReactedAt: Instant?,
        val boost: Long = 0,
        val selfBoost: Long = 0,
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
                    boost = entry.boost,
                    selfBoost = entry.selfBoost,
                ),
            )
        }
        for ((emoji, held) in confirmedByEmoji.toMap()) {
            if (emoji !in present && desired[emoji] == null && held.count > 0) {
                confirmedByEmoji[emoji] = held.copy(count = 0, selfReacted = false, selfReactedAt = null, boost = 0, selfBoost = 0)
            }
        }
    }

    /**
     * Accepts one stream update; [count] and [boost] are the emoji's new totals, not deltas, and
     * [actorBoost] is the actor's own boost after it.
     */
    fun applyUpdate(
        emoji: String,
        actorIsSelf: Boolean,
        added: Boolean,
        count: Long,
        version: Long,
        reactedAt: Instant?,
        boost: Long = 0,
        actorBoost: Long = 0,
    ) {
        val held = confirmedByEmoji[emoji]
        val selfReacted: Boolean
        val selfReactedAt: Instant?
        val selfBoost: Long
        if (actorIsSelf) {
            selfReacted = added
            selfReactedAt = if (added) reactedAt ?: held?.selfReactedAt else null
            // actorBoost is 0 on a plain ADDED, which must not wipe a boost the user already paid.
            selfBoost = if (added) maxOf(actorBoost, held?.selfBoost ?: 0) else 0
        } else {
            selfReacted = held?.selfReacted ?: false
            selfReactedAt = held?.selfReactedAt
            selfBoost = held?.selfBoost ?: 0
        }
        accept(
            emoji,
            Confirmed(
                count = count,
                selfReacted = selfReacted,
                version = version,
                selfReactedAt = selfReactedAt,
                boost = boost,
                selfBoost = selfBoost,
            ),
        )
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
                boost = state.boost,
                selfBoost = state.selfBoost,
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
        // The server refuses to take back a boosted reaction, so the tap does nothing.
        if (isSelfBoosted(emoji)) return null
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
                // An add or remove response carries no boost, and neither call changes it.
                val held = confirmedByEmoji[emoji]
                accept(
                    emoji,
                    Confirmed(
                        count = result.count,
                        selfReacted = result.selfReacted,
                        version = result.version,
                        selfReactedAt = result.selfReactedAt,
                        boost = held?.boost ?: 0,
                        selfBoost = if (result.selfReacted) held?.selfBoost ?: 0 else 0,
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
                ReactionPill(
                    emoji = emoji,
                    count = count,
                    selfReacted = shown,
                    pending = desired[emoji] != null,
                    boost = confirmedByEmoji[emoji]?.boost?.takeIf { it > 0 }?.let(::ReactionBoost),
                    selfBoosted = isSelfBoosted(emoji),
                )
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

    /** Whether the user has paid to boost [emoji], which makes their reaction permanent. */
    fun isSelfBoosted(emoji: String): Boolean = (confirmedByEmoji[emoji]?.selfBoost ?: 0) > 0

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
