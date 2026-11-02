package com.flipcash.shared.chat.internal.delegates

import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.TypingNotification
import com.flipcash.services.models.chat.TypingState
import com.flipcash.shared.chat.ActiveTypist
import com.flipcash.shared.chat.ChatState
import com.flipcash.shared.chat.internal.ChatStateHolder
import com.getcode.opencode.model.core.ID
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.TimeSource

/**
 * Other members' typing state per chat, and the only writer of [ChatState.typingIndicators].
 *
 * The server relays typing best-effort with no timeout of its own, so a lost STOPPED (the sender
 * backgrounded or killed, a dropped or reopened stream) would leave a typist on screen for good.
 * Every STARTED/STILL gives the typist a deadline [expiry] out; one job, armed for the earliest
 * deadline across all chats, drops whoever has passed theirs and re-arms for the next. Matches
 * iOS's `ConversationTyping`.
 *
 * Mutations happen under a lock: notifications arrive on the stream collector and expiry runs on
 * its own job, both on the coordinator's multi-threaded scope.
 */
internal class TypingIndicatorTracker(
    private val stateHolder: ChatStateHolder,
    private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
    private val expiry: Duration = INCOMING_EXPIRY,
    private val stoppedLinger: Duration = STOPPED_LINGER,
    private val arrivalHold: Duration = ARRIVAL_HOLD,
    private val now: () -> Instant = { Clock.System.now() },
) {

    companion object {
        private const val TAG = "TypingIndicatorTracker"

        // Both clients send STILL every 3s while typing, so this rides out a couple of lost ones.
        // Same value as iOS's `incomingExpiry`.
        val INCOMING_EXPIRY = 10.seconds

        // The sender's composer empties on submit, which sends STOPPED before the message itself
        // goes out, so STOPPED routinely lands first. Holding the dots through that gap lets the
        // message take them away ([messageArrived]); a STOPPED with nothing behind it (the draft
        // was deleted) just leaves this much later. iOS's `defaultStoppedLinger`.
        val STOPPED_LINGER = 800.milliseconds

        // How long a typist stays listed after their message arrives. The transcript draws the
        // dots as its newest row and grows the message out of them, but the message reaches the
        // paged list a beat after it reaches here; dropping the typist on arrival would take the
        // dots away first and leave nothing to grow out of. The transcript lets go of the typist
        // itself as soon as the message lands, so this only runs out when it never does.
        val ARRIVAL_HOLD = 1500.milliseconds
    }

    /** [arrived] marks a typist held for their message to land; see [ARRIVAL_HOLD]. */
    private class Typist(val since: Instant, val deadline: ComparableTimeMark, val arrived: Boolean = false)

    private val lock = Any()
    private val typists = mutableMapOf<ChatId, MutableMap<ID, Typist>>()
    private var scope: CoroutineScope? = null
    private var expiryJob: Job? = null

    fun initialize(scope: CoroutineScope) = synchronized(lock) {
        this.scope = scope
    }

    /** Applies [notifications] for [chatId], ignoring the signed-in user's own ([selfId]). */
    fun apply(chatId: ChatId, notifications: List<TypingNotification>, selfId: ID?) = synchronized(lock) {
        for (notification in notifications) {
            if (notification.userId == selfId) continue
            when (notification.state) {
                TypingState.STARTED_TYPING, TypingState.STILL_TYPING -> {
                    val chat = typists.getOrPut(chatId) { mutableMapOf() }
                    // A STILL heartbeat extends the deadline but keeps the typist's place in line.
                    // Typing again after a message is a new session, with a new place.
                    val since = chat[notification.userId]?.takeUnless { it.arrived }?.since ?: now()
                    chat[notification.userId] = Typist(since, timeSource.markNow() + expiry)
                }
                TypingState.STOPPED_TYPING, TypingState.TYPING_TIMED_OUT -> {
                    // Lingers rather than leaving at once; someone not typing is never added back.
                    val chat = typists[chatId] ?: continue
                    val typist = chat[notification.userId] ?: continue
                    // Already held for their message; the STOPPED that trails a send doesn't cut it short.
                    if (typist.arrived) continue
                    chat[notification.userId] = Typist(typist.since, timeSource.markNow() + stoppedLinger)
                }
                TypingState.UNKNOWN -> Unit
            }
        }
        publish()
        scheduleExpiry()
    }

    /**
     * Ends the typing of [senderIds] in [chatId] as their messages arrive: any linger is cut to
     * [ARRIVAL_HOLD], and a later STOPPED doesn't reopen it. They stay listed for that long so the
     * transcript can hand their dots to the message; see [ARRIVAL_HOLD].
     */
    fun messageArrived(chatId: ChatId, senderIds: Collection<ID>) = synchronized(lock) {
        val chat = typists[chatId] ?: return@synchronized
        var changed = false
        for (senderId in senderIds) {
            val typist = chat[senderId] ?: continue
            if (typist.arrived) continue
            chat[senderId] = Typist(typist.since, timeSource.markNow() + arrivalHold, arrived = true)
            changed = true
        }
        if (!changed) return@synchronized
        publish()
        scheduleExpiry()
    }

    /** Drops every typist and stops the expiry job. */
    fun clear() = synchronized(lock) {
        typists.clear()
        expiryJob?.cancel()
        expiryJob = null
        publish()
    }

    private fun remove(chatId: ChatId, userId: ID) {
        val chat = typists[chatId] ?: return
        chat.remove(userId)
        if (chat.isEmpty()) typists.remove(chatId)
    }

    private fun scheduleExpiry() {
        expiryJob?.cancel()
        expiryJob = null
        val earliest = typists.values.flatMap { it.values }.minOfOrNull { it.deadline } ?: return
        val scope = scope ?: return
        expiryJob = scope.launch {
            // Negative while the deadline is ahead of us.
            delay(-earliest.elapsedNow())
            sweep()
        }
    }

    private fun sweep() = synchronized(lock) {
        val current = timeSource.markNow()
        for ((chatId, chat) in typists.entries.toList()) {
            for ((userId, typist) in chat.entries.toList()) {
                if (typist.deadline <= current) {
                    trace(tag = TAG, message = "Expiring stale typist in $chatId", type = TraceType.Process)
                    remove(chatId, userId)
                }
            }
        }
        publish()
        scheduleExpiry()
    }

    private fun publish() {
        val snapshot = typists.mapValues { (_, chat) ->
            chat.map { (userId, typist) -> ActiveTypist(userId = userId, since = typist.since, arrived = typist.arrived) }.toSet()
        }
        stateHolder.update { it.copy(typingIndicators = snapshot) }
    }
}
