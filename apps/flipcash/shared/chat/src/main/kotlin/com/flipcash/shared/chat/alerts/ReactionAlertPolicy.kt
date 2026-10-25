package com.flipcash.shared.chat.alerts

import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.ReactionUpdate
import com.getcode.opencode.model.core.ID

/**
 * What the stream knows about a reaction worth telling the user about, before names are resolved.
 * [boostDeltaCents] is set only for [ReactionUpdate.Action.BOOSTED].
 */
data class ReactionAlertEvent(
    val chatId: ChatId,
    val messageId: Long,
    val emoji: String,
    val actor: ID,
    val action: ReactionUpdate.Action,
    val boostDeltaCents: Long?,
    val actorName: String?,
    val chatName: String?,
    val quote: String,
)

/**
 * The trigger rules from `docs/specs/in-app-reaction-alerts.md`. Pure so each rule is a test.
 *
 * Foreground and on-screen are presentation concerns and live in [ReactionAlertController].
 */
object ReactionAlertPolicy {

    /** What the stream should do with an update whose message may not be held locally. */
    sealed interface Decision {
        data object Drop : Decision

        /** Rules 2 and 3 hold but the message's sender is unknown: fetch the message, then decide again. */
        data object NeedsMessage : Decision
        data object Alert : Decision
    }

    /**
     * @param message the local copy of the message, or null when it is not cached.
     */
    fun decide(update: ReactionUpdate, selfId: ID?, message: ChatMessage?): Decision {
        if (selfId == null) return Decision.Drop
        // 3. REMOVED (and UNKNOWN) show nothing.
        if (update.action != ReactionUpdate.Action.ADDED && update.action != ReactionUpdate.Action.BOOSTED) {
            return Decision.Drop
        }
        // 2. Reactions from the user's other device arrive on the same stream.
        if (update.actor == selfId) return Decision.Drop
        // 1. The message must be the user's own.
        if (message == null) return Decision.NeedsMessage
        return if (isSentBy(message, selfId)) Decision.Alert else Decision.Drop
    }

    private fun isSentBy(message: ChatMessage, selfId: ID): Boolean =
        message.isFromSelf || message.senderId == selfId

    /**
     * `actor_boost_usd` is the actor's new total on the emoji, so this boost is the difference from
     * the total held before. With no earlier total the new total is shown.
     */
    fun boostDeltaCents(newTotal: Long, previousTotal: Long?): Long {
        if (previousTotal == null) return newTotal
        return (newTotal - previousTotal).coerceAtLeast(0)
    }

    /** The text the pill quotes: the message's text, or the text of the reply it carries. */
    fun quoteOf(message: ChatMessage?): String {
        val content = message?.content ?: return ""
        for (part in content) {
            when (part) {
                is MessageContent.Text -> return part.text
                is MessageContent.Reply -> part.content.filterIsInstance<MessageContent.Text>().firstOrNull()?.let { return it.text }
                else -> Unit
            }
        }
        return ""
    }
}

/**
 * The last total each actor held on each emoji, as seen on the stream. `ReactionState` keeps the
 * emoji's total and the viewer's own part but no per-actor figure, so the boost delta is read from
 * here. An ADDED seeds a total of zero, so a first boost after it is a true delta.
 */
class ActorBoostTotals {
    private data class Key(val chatId: ChatId, val messageId: Long, val emoji: String, val actor: List<Byte>)

    private val totals = mutableMapOf<Key, Long>()

    fun previous(chatId: ChatId, messageId: Long, emoji: String, actor: ID): Long? =
        totals[Key(chatId, messageId, emoji, actor)]

    fun record(chatId: ChatId, messageId: Long, emoji: String, actor: ID, total: Long) {
        totals[Key(chatId, messageId, emoji, actor)] = total
    }

    fun clear() = totals.clear()
}
