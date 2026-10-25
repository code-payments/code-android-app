package com.flipcash.shared.chat.alerts

import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ReactionUpdate

/** What one pill shows. Titles are composed by the UI from these fields, so they can be localised. */
data class ReactionAlertContent(
    val chatId: ChatId,
    val messageId: Long,
    val kind: Kind,
    val chatName: String?,
    val quote: String,
    /** Distinct actors, in the order they arrived. Names are null when not resolved. */
    val actorNames: List<String?>,
    /** Distinct emoji, in the order they arrived. The pill shows at most [MAX_TRAILING_EMOJI]. */
    val emojis: List<String>,
    /** The boost this alert reports, for [Kind.Boost]. */
    val boostCents: Long? = null,
) {
    enum class Kind { Reaction, Boost, Several }

    /** 2.5 s, or 3.5 s for a boost, which carries an amount to read. */
    val holdMillis: Long get() = if (kind == Kind.Boost) BOOST_HOLD_MILLIS else HOLD_MILLIS

    val trailingEmojis: List<String> get() = emojis.take(MAX_TRAILING_EMOJI)

    companion object {
        const val HOLD_MILLIS = 2_500L
        const val BOOST_HOLD_MILLIS = 3_500L
        const val MAX_TRAILING_EMOJI = 3

        fun from(event: ReactionAlertEvent): ReactionAlertContent {
            val boosted = event.action == ReactionUpdate.Action.BOOSTED
            return ReactionAlertContent(
                chatId = event.chatId,
                messageId = event.messageId,
                kind = if (boosted) Kind.Boost else Kind.Reaction,
                chatName = event.chatName,
                quote = event.quote,
                actorNames = listOf(event.actorName),
                emojis = listOf(event.emoji),
                boostCents = if (boosted) event.boostDeltaCents else null,
            )
        }
    }
}

/**
 * One pill at a time. Events for the pill's own message merge into it and restart its hold; events
 * for another message wait their turn, merging with each other when they share a message.
 *
 * Pure bookkeeping: the clock and the hold timer belong to [ReactionAlertController].
 */
class ReactionAlertQueue {

    enum class Outcome {
        /** Nothing was showing; the event is now the current pill and its hold should start. */
        Started,

        /** Merged into the current pill; its hold should restart. */
        Merged,

        /** Waiting behind the current pill. */
        Queued,
    }

    var current: ReactionAlertContent? = null
        private set

    private val pending = ArrayDeque<ReactionAlertContent>()

    val pendingCount: Int get() = pending.size

    /**
     * [actorId] tells actors apart for the several-actors state; the event's actor name alone would
     * merge two people with the same name, or two whose names did not resolve.
     */
    fun offer(event: ReactionAlertEvent): Outcome {
        val incoming = ReactionAlertContent.from(event)
        val shown = current
        if (shown == null) {
            current = incoming
            actors[key(incoming)] = mutableListOf(event.actor)
            return Outcome.Started
        }
        if (sameMessage(shown, incoming)) {
            current = merge(shown, incoming, event.actor)
            return Outcome.Merged
        }
        val index = pending.indexOfFirst { sameMessage(it, incoming) }
        if (index >= 0) {
            pending[index] = merge(pending[index], incoming, event.actor)
        } else {
            pending.addLast(incoming)
            actors[key(incoming)] = mutableListOf(event.actor)
        }
        return Outcome.Queued
    }

    /** Closes the current pill and starts the next waiting one, if any. */
    fun advance(): ReactionAlertContent? {
        current?.let { actors.remove(key(it)) }
        current = pending.removeFirstOrNull()
        return current
    }

    /** Drops the current pill and everything waiting. */
    fun clear() {
        current = null
        pending.clear()
        actors.clear()
    }

    private val actors = mutableMapOf<Pair<List<Byte>, Long>, MutableList<List<Byte>>>()

    private fun key(content: ReactionAlertContent) = content.chatId.bytes.toList() to content.messageId

    private fun sameMessage(a: ReactionAlertContent, b: ReactionAlertContent) =
        a.chatId == b.chatId && a.messageId == b.messageId

    private fun merge(held: ReactionAlertContent, incoming: ReactionAlertContent, actor: List<Byte>): ReactionAlertContent {
        val known = actors.getOrPut(key(held)) { mutableListOf() }
        val isNewActor = actor !in known
        if (isNewActor) known.add(actor)
        val names = if (isNewActor) held.actorNames + incoming.actorNames else held.actorNames
        val several = known.size >= 2
        val emojis = if (several) {
            (held.emojis + incoming.emojis).distinct()
        } else {
            // One actor: the newest event swaps the content in place.
            incoming.emojis
        }
        return held.copy(
            kind = when {
                several -> ReactionAlertContent.Kind.Several
                else -> incoming.kind
            },
            actorNames = names,
            emojis = emojis,
            boostCents = if (several) null else incoming.boostCents,
            quote = incoming.quote.ifEmpty { held.quote },
            chatName = incoming.chatName ?: held.chatName,
        )
    }
}
