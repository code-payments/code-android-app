package com.flipcash.shared.chat.reactions

import kotlin.math.max
import kotlin.math.min

/**
 * The pills one on-screen row shows, catching up with [ReactionOrdering] on a beat rather than
 * jumping the moment a count changes, so a thumb on its way to a pill still lands on it.
 *
 * This is `gen_reactions.py`'s `Settler`, and `reactions.json`'s `settle` vectors hold it to that.
 * Counts and boosts change live through [update]; positions, joins, leaves and widths change only
 * in [settle], at most once per [Config.settleMs]:
 * - a beat makes at most [Config.maxSwaps] neighbour swaps, nearest the top first;
 * - a pill passes its neighbour on any boost lead, or a count lead of [Config.countMargin]; a tie
 *   never moves one;
 * - a pair that swapped cannot swap back for [Config.flapCooldownMs];
 * - a [touch] holds the row for [Config.holdAfterTouchMs], extended by later touches but never
 *   past [Config.maxHoldMs] from the first;
 * - an emptied pill shows 0 in place until the beat, and a new emoji joins at the end on one.
 *
 * Each shown pill reserves room for its count (at least [MIN_COUNT_DIGITS] digits) and boost as
 * they stood at the last beat. A value that fits shows at once; one that needs more room keeps the
 * old value until the next beat, so a pill's width never changes between beats.
 *
 * Pure Kotlin with an injected [clock] (monotonic milliseconds) so it can move into the shared core
 * and tests can drive it. Not thread-safe: one row owns it.
 */
class ReactionSettler(
    pills: List<ReactionPill>,
    private val clock: () -> Long,
    private val config: Config = Config(),
) {

    /** The settle rules, equal to `settleDefaults` in `reactions.json`. */
    data class Config(
        val settleMs: Long = 1_000,
        val maxSwaps: Int = 3,
        val countMargin: Long = 1,
        val flapCooldownMs: Long = 3_000,
        val holdAfterTouchMs: Long = 1_500,
        val maxHoldMs: Long = 4_000,
        val hitGraceMs: Long = HIT_GRACE_MS,
    )

    /**
     * One pill as the row draws it: [pill] carries the values to show, [countDigits] and
     * [boostDigits] the room to reserve for them (a [boostDigits] of 0 reserves none).
     */
    data class Shown(val pill: ReactionPill, val countDigits: Int, val boostDigits: Int)

    private class Reservation(val countDigits: Int, val boostDigits: Int)

    private var live: Map<String, ReactionPill> = pills.associateBy { it.emoji }
    private var order: List<String> = target()
    private val reserved = HashMap<String, Reservation>()
    private val displayed = HashMap<String, ReactionPill>()
    private var lastSettle: Long = clock()
    private var holdStart: Long? = null
    private var holdUntil: Long? = null
    private var hitOrder: List<String> = emptyList()
    private var hitUntil: Long? = null
    private val pairSwaps = HashMap<Set<String>, Long>()

    init {
        reserveAll()
    }

    /** The pills to draw, in their settled order. */
    val shown: List<Shown>
        get() = order.map { emoji ->
            val reservation = reserved.getValue(emoji)
            Shown(displayed.getValue(emoji), reservation.countDigits, reservation.boostDigits)
        }

    /** When [settle] can next move anything: the next beat, or the end of a hold. */
    val nextBeatAt: Long
        get() = max(lastSettle + config.settleMs, holdUntil ?: Long.MIN_VALUE)

    /**
     * Whether a beat has anything to do: a pill to join or leave, a width to change, or a pair out
     * of order by more than a tie. The row's beat loop can idle while this is false.
     */
    val needsBeat: Boolean
        get() {
            if (order.any { liveCount(it) <= 0 }) return true
            if (live.values.any { it.count > 0 && it.emoji !in order }) return true
            if (order.any { !sameReservation(reserved.getValue(it), reservationFor(live.getValue(it))) }) return true
            return order.zipWithNext().any { (above, below) -> overtakes(live.getValue(below), live.getValue(above)) }
        }

    /** Takes the latest merged pills; their values show at once where they fit. */
    fun update(pills: List<ReactionPill>) {
        live = pills.associateBy { it.emoji }
        for (emoji in order) {
            val old = displayed.getValue(emoji)
            val now = live[emoji]?.takeIf { it.count > 0 } ?: old.copy(count = 0, selfReacted = false, boost = null)
            val reservation = reserved.getValue(emoji)
            displayed[emoji] = now.copy(
                count = if (digits(now.count) <= reservation.countDigits) now.count else old.count,
                boost = if (boostDigits(now.boost) <= reservation.boostDigits) now.boost else old.boost,
            )
        }
    }

    /** A touch-down on the row: nothing moves until the hold this starts or extends ends. */
    fun touch() {
        val now = clock()
        val until = holdUntil
        if (until == null || now >= until) {
            holdStart = now
            holdUntil = min(now + config.holdAfterTouchMs, now + config.maxHoldMs)
        } else {
            holdUntil = max(until, min(now + config.holdAfterTouchMs, holdStart!! + config.maxHoldMs))
        }
    }

    /**
     * Runs a beat if one is due and the row is not held. Returns whether anything shown changed:
     * an order, a join or leave, a value that was waiting for room, or a width.
     */
    fun settle(): Boolean {
        val now = clock()
        if (holdUntil?.let { now < it } == true || now - lastSettle < config.settleMs) return false
        lastSettle = now
        pairSwaps.values.removeAll { now - it >= config.flapCooldownMs }

        val before = order
        val next = order.filterTo(ArrayList()) { liveCount(it) > 0 }
        target().filterTo(next) { it !in next }
        var swaps = 0
        while (swaps < config.maxSwaps) {
            val i = (0 until next.size - 1).firstOrNull { i ->
                setOf(next[i], next[i + 1]) !in pairSwaps && overtakes(live.getValue(next[i + 1]), live.getValue(next[i]))
            } ?: break
            pairSwaps[setOf(next[i], next[i + 1])] = now
            next[i] = next[i + 1].also { next[i + 1] = next[i] }
            swaps++
        }

        val shownBefore = shown
        order = next
        reserveAll()
        if (next != before) {
            hitOrder = before
            hitUntil = now + config.hitGraceMs
        }
        return shown != shownBefore
    }

    /**
     * The emoji a tap on slot [index] lands on, or null for the "+" pill or empty space. For
     * [Config.hitGraceMs] after a beat reorders the row, that is the slot in the order before it.
     */
    fun hit(index: Int): String? {
        val order = if (hitUntil?.let { clock() < it } == true) hitOrder else order
        return order.getOrNull(index)
    }

    private fun target(): List<String> =
        ReactionOrdering.sorted(live.values.filter { it.count > 0 }).map { it.emoji }

    private fun liveCount(emoji: String): Long = live[emoji]?.count ?: 0

    private fun overtakes(below: ReactionPill, above: ReactionPill): Boolean {
        val belowBoost = below.boost?.total ?: 0L
        val aboveBoost = above.boost?.total ?: 0L
        if (belowBoost != aboveBoost) return belowBoost > aboveBoost
        return below.count - above.count >= config.countMargin
    }

    private fun reserveAll() {
        reserved.keys.retainAll(order.toSet())
        displayed.keys.retainAll(order.toSet())
        for (emoji in order) {
            val pill = live.getValue(emoji)
            reserved[emoji] = reservationFor(pill)
            displayed[emoji] = pill
        }
    }

    private fun reservationFor(pill: ReactionPill) =
        Reservation(countDigits = max(MIN_COUNT_DIGITS, digits(pill.count)), boostDigits = boostDigits(pill.boost))

    private fun sameReservation(a: Reservation, b: Reservation) =
        a.countDigits == b.countDigits && a.boostDigits == b.boostDigits

    companion object {
        /**
         * How long after a layout change a tap still resolves against the layout before it. Tuned
         * in the prototype for a thumb that re-aims mid-travel; nobody has measured real thumbs on
         * a phone yet, and a thumb that commits early would want 600 to 700 ms. Keep it here, in
         * one place, so that measurement can move it.
         */
        const val HIT_GRACE_MS: Long = 450

        /** The fewest count digits a pill reserves room for, so 9 to 10 does not widen it. */
        const val MIN_COUNT_DIGITS: Int = 2

        private fun digits(value: Long): Int = value.coerceAtLeast(0).toString().length

        private fun boostDigits(boost: ReactionBoost?): Int = boost?.total?.takeIf { it > 0 }?.let(::digits) ?: 0
    }
}
