package com.flipcash.shared.chat.internal.delegates

import com.flipcash.app.analytics.Analytics
import com.flipcash.app.analytics.FlipcashAnalyticsService
import com.flipcash.app.persistence.sources.ChatMemberDataSource
import com.flipcash.app.persistence.sources.ChatMessageDataSource
import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.services.controllers.ChatMessagingController
import com.flipcash.services.controllers.EventStreamingController
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.ChatUpdate
import com.flipcash.services.models.chat.EmojiReaction
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.MetadataUpdate
import com.flipcash.services.models.chat.ReactionSummary
import com.flipcash.services.models.chat.ReactionUpdate
import com.flipcash.services.models.chat.TypingNotification
import com.flipcash.services.models.chat.TypingState
import com.flipcash.services.models.GetDeltaError
import com.flipcash.shared.chat.ActiveTypist
import com.flipcash.shared.chat.EventSequenceTracker
import com.flipcash.shared.chat.EventStreamOperations
import com.flipcash.shared.chat.internal.ChatStateHolder
import com.flipcash.services.user.UserManager
import com.getcode.opencode.exchange.Exchange
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Manages the server-sent event stream and applies real-time [ChatUpdate]s to
 * local persistence and in-memory state.
 *
 * Responsibilities:
 * - **Message persistence** — resolves messages from `ChatUpdate.events` (preferred)
 *   or the deprecated `newMessages` field, then upserts to Room.
 * - **Gap-aware event sequencing** — uses [EventSequenceTracker] to maintain a
 *   contiguous frontier. Only the highest contiguous sequence is persisted; if a
 *   gap is detected, a timed [getDelta][performDeltaSync] backfill is scheduled.
 * - **Reaction overlays** — merges [ReactionUpdate]s into an in-memory
 *   `Map<ChatId, Map<Long, ReactionSummary>>` with last-writer-wins on
 *   `EmojiReaction.sequence`.
 * - **Typing indicators** — maintains per-chat `Set<ActiveTypist>` in [ChatStateHolder].
 * - **Eager balance update** — credits incoming cash messages to [TokenCoordinator]
 *   before the server balance refresh arrives.
 * - **Heartbeat** — periodically checks stream liveness and reconnects if dead.
 *
 * **Cross-delegate communication:** Emits [Event.SyncFeedRequested] when a message
 * arrives for an unknown chat, and [Event.LoadMessages] when a delta reset requires
 * a full message re-fetch. [RealChatCoordinator] routes these.
 *
 * Requires [initialize] with a [CoroutineScope] before any work can be launched.
 *
 * @see com.flipcash.shared.chat.internal.RealChatCoordinator
 * @see EventSequenceTracker
 */
@Singleton
class EventStreamDelegate @Inject constructor(
    private val eventStreamingController: EventStreamingController,
    private val messagingController: ChatMessagingController,
    private val metadataDataSource: ChatMetadataDataSource,
    private val messageDataSource: ChatMessageDataSource,
    private val memberDataSource: ChatMemberDataSource,
    private val tokenCoordinator: TokenCoordinator,
    private val userManager: UserManager,
    private val stateHolder: ChatStateHolder,
    private val analytics: FlipcashAnalyticsService,
    private val exchange: Exchange,
) : EventStreamOperations {

    companion object {
        private const val TAG = "EventStreamDelegate"
        private val GAP_FILL_DELAY = 2.seconds
        private val HEARTBEAT_INTERVAL = 30.seconds
        private val REOPEN_BACKOFF_BASE = 1.seconds
    }

    sealed interface Event {
        data object SyncFeedRequested : Event
        data class LoadMessages(val chatId: ChatId) : Event
    }

    private val _events = Channel<Event>(Channel.UNLIMITED)
    val events: Flow<Event> = _events.receiveAsFlow()

    private val sequenceTracker = EventSequenceTracker()
    private var scope: CoroutineScope? = null
    private var eventStreamCollectJob: Job? = null
    private var heartbeatJob: Job? = null

    // region EventStreamOperations

    override fun observeTypingIndicators(chatId: ChatId): Flow<Set<ActiveTypist>> {
        return stateHolder.state.map { it.typingIndicators[chatId] ?: emptySet() }
    }

    override fun observeReactions(chatId: ChatId, messageId: Long): Flow<ReactionSummary?> {
        return stateHolder.state.map { it.reactionOverlays[chatId]?.get(messageId) }
            .distinctUntilChanged()
    }

    // endregion

    // region Internal

    /**
     * Increments the per-user received counters for one inbound message.
     *
     * A tip is also a message, so a tip increments both counters — `Messages
     * Received` is a total, not a non-tip remainder.
     */
    private fun countReceipt(msg: ChatMessage) {
        analytics.incrementReceivedCounter(Analytics.ReceivedCounter.Messages)

        val tip = msg.content
            .filterIsInstance<MessageContent.Cash>()
            .firstOrNull { it.action == MessageContent.Cash.Action.TIPPED }
            ?: return

        analytics.incrementReceivedCounter(Analytics.ReceivedCounter.Tips)

        // Chat cash arrives in the SENDER's native currency. With no cached rate
        // we count the tip but skip its value: an understated total is
        // recoverable, a wrong one is permanent and unattributable.
        val usd = exchange.rateToUsd(tip.amount.currencyCode)
            ?.let { tip.amount.convertingTo(it) }
            ?: return

        analytics.incrementReceivedCounter(Analytics.ReceivedCounter.TipsValue, usd.decimalValue)
    }

    internal fun initialize(scope: CoroutineScope) {
        this.scope = scope
    }

    internal fun open() {
        val scope = scope ?: return
        if (eventStreamingController.isConnected) {
            trace(tag = TAG, message = "Event stream already connected, skipping open", type = TraceType.Process)
            ensureCollector(scope)
            return
        }

        eventStreamingController.open(scope)
        ensureCollector(scope)
    }

    internal fun close() {
        eventStreamCollectJob?.cancel()
        eventStreamCollectJob = null
        eventStreamingController.close()
    }

    /**
     * Supervises the stream: whenever it is found down, syncs the feed and reopens it.
     *
     * Each pass waits for whichever comes first, a [HEARTBEAT_INTERVAL] tick or
     * [EventStreamingController.streamFailures] reporting a stream that ended with nothing
     * retrying it. The failure signal is what keeps such a stream from costing a whole interval:
     * a status outside the reconnect loop's retryable set, or a ping timeout, is never retried by
     * the loop, and the tick was the only thing that noticed.
     *
     * Reopening backs off from the second consecutive attempt on, so a stream that fails as soon
     * as it opens cannot spin the loop. The backoff tops out at [HEARTBEAT_INTERVAL], which is
     * where every failure used to wait.
     */
    internal fun startHeartbeat(onReconnect: () -> Unit) {
        val scope = scope ?: return
        stopHeartbeat()
        heartbeatJob = scope.launch {
            var consecutiveReopens = 0

            while (true) {
                // A failures flow that completes means no signal is coming, not that one just
                // arrived — waiting out the tick keeps this a supervisor rather than a spin.
                val failed = withTimeoutOrNull(HEARTBEAT_INTERVAL) {
                    eventStreamingController.streamFailures.firstOrNull() ?: awaitCancellation()
                } != null

                // Only the failure-driven wake can arrive back to back; the tick already spaces
                // itself out. Liveness is read after the wait, so a stream another trigger
                // reopened in the meantime is left alone.
                if (failed) delay(reopenBackoff(consecutiveReopens))

                if (eventStreamingController.isStreamActive) {
                    consecutiveReopens = 0
                    continue
                }
                consecutiveReopens++

                trace(tag = TAG, message = "Event stream down, syncing feed and reconnecting", type = TraceType.Process)
                onReconnect()
                eventStreamingController.close()
                open()
            }
        }
    }

    private fun reopenBackoff(consecutiveReopens: Int): Duration = when {
        consecutiveReopens <= 0 -> Duration.ZERO
        else -> (REOPEN_BACKOFF_BASE * (1 shl (consecutiveReopens - 1).coerceAtMost(5)))
            .coerceAtMost(HEARTBEAT_INTERVAL)
    }

    internal fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    /**
     * Backfills a chat from its applied cursor onward.
     *
     * The cursor is read from `chat_metadata`, which holds what the client has actually applied:
     * nothing writes the server's head there — a feed sync refreshes only the server-owned columns
     * (`ChatMetadataDao.upsert`), and the mapper drops the head on the way in.
     */
    internal suspend fun performDeltaSync(chatId: ChatId) {
        val afterSequence = metadataDataSource.getLatestEventSequence(chatId)
        trace(tag = TAG, message = "Delta sync for $chatId from sequence $afterSequence", type = TraceType.Process)

        try {
            val result = messagingController.getDelta(chatId, afterSequence).first()
            result
                .onSuccess { delta ->
                    if (delta.messages.isNotEmpty()) {
                        messageDataSource.upsert(chatId, delta.messages)
                        val latest = delta.messages.maxByOrNull { it.messageId }
                        latest?.let { msg ->
                            metadataDataSource.updateLastMessageId(chatId, msg.messageId)
                            // Only advance lastActivity — a partial page from a
                            // delta sync must not regress it to an older timestamp.
                            val existing = metadataDataSource.getLastActivity(chatId)
                            val incoming = msg.timestamp.toEpochMilliseconds()
                            if (existing == null || incoming > existing) {
                                metadataDataSource.updateLastActivity(chatId, incoming)
                            }
                        }
                    }
                    if (delta.latestSequence > afterSequence) {
                        metadataDataSource.updateLatestEventSequence(chatId, delta.latestSequence)
                    }
                    sequenceTracker.resetTo(chatId, delta.latestSequence)
                    trace(tag = TAG, message = "Delta sync complete: ${delta.messages.size} messages, sequence ${delta.latestSequence}", type = TraceType.Process)
                }
                .onFailure { error ->
                    if (error is GetDeltaError.ResetRequired) {
                        trace(tag = TAG, message = "Delta sync reset required for $chatId, falling back to full load", type = TraceType.Process)
                        sequenceTracker.clear(chatId)
                        _events.send(Event.LoadMessages(chatId))
                    } else {
                        trace(tag = TAG, message = "Delta sync failed for $chatId: ${error.message}", type = TraceType.Error)
                    }
                }
        } catch (e: Exception) {
            trace(tag = TAG, message = "Delta sync exception for $chatId: ${e.message}", type = TraceType.Error)
        }
    }

    internal fun clearAll() {
        sequenceTracker.clearAll()
    }

    private fun ensureCollector(scope: CoroutineScope) {
        if (eventStreamCollectJob?.isActive != true) {
            eventStreamCollectJob = scope.launch {
                eventStreamingController.chatUpdates.collect { applyUpdate(it) }
            }
        }
    }

    private suspend fun applyUpdate(update: ChatUpdate) {
        val chatId = update.chatId

        // --- Resolve messages: prefer events, fall back to deprecated newMessages ---

        val resolvedMessages = if (update.events.isNotEmpty()) {
            update.events
                .flatMap { event -> event.mutations.map { it.message } }
                .sortedBy { it.eventSequence }
                .distinctBy { it.messageId }
        } else {
            @Suppress("DEPRECATION")
            update.newMessages
        }

        trace(
            tag = TAG,
            message = "applyUpdate: chatId=$chatId, messages=${resolvedMessages.size}, events=${update.events.size}, pointers=${update.pointerUpdates.size}, reactions=${update.reactionUpdates.size}, typing=${update.typingNotifications.size}",
            type = TraceType.Process,
        )

        // --- Persist to DB ---

        val lastMsg = if (resolvedMessages.isNotEmpty()) {
            trace(tag = TAG, message = "Upserting ${resolvedMessages.size} messages for $chatId", type = TraceType.Process)
            messageDataSource.upsert(chatId, resolvedMessages)
            resolvedMessages.maxByOrNull { it.messageId }
        } else null

        // Advance event sequence cursor — gap-aware
        if (update.events.isNotEmpty()) {
            val dbCursor = metadataDataSource.getLatestEventSequence(chatId)
            val incomingSequences = update.events.map { it.sequence }
            val result = sequenceTracker.processSequences(chatId, dbCursor, incomingSequences)

            if (result.newContiguousSequence > dbCursor) {
                metadataDataSource.updateLatestEventSequence(chatId, result.newContiguousSequence)
            }

            if (result.hasGap) {
                scheduleGapFill(chatId)
            }
        }

        for (pointer in update.pointerUpdates) {
            memberDataSource.updatePointers(chatId, pointer)
        }

        // Process metadata updates before message-derived fields so that a
        // FullRefresh (which replaces the entire entity) doesn't overwrite
        // the lastActivity/lastMessageId set from the incoming message.
        for (metaUpdate in update.metadataUpdates) {
            when (metaUpdate) {
                is MetadataUpdate.FullRefresh -> {
                    metadataDataSource.upsert(metaUpdate.metadata)
                    memberDataSource.replaceMembers(
                        metaUpdate.metadata.chatId,
                        metaUpdate.metadata.members,
                    )
                    metaUpdate.metadata.lastMessage?.let { msg ->
                        messageDataSource.upsert(metaUpdate.metadata.chatId, listOf(msg))
                    }
                }
                is MetadataUpdate.LastActivityChanged -> {
                    metadataDataSource.updateLastActivity(
                        chatId,
                        metaUpdate.newLastActivity.toEpochMilliseconds(),
                    )
                }
            }
        }

        // Update lastMessageId and lastActivity AFTER metadata updates so that
        // incoming message timestamps always take precedence over a potentially
        // stale FullRefresh.
        lastMsg?.let { msg ->
            metadataDataSource.updateLastMessageId(chatId, msg.messageId)
            metadataDataSource.updateLastActivity(chatId, msg.timestamp.toEpochMilliseconds())
        }

        // --- Process reaction updates ---

        if (update.reactionUpdates.isNotEmpty()) {
            stateHolder.update { state ->
                val chatOverlays = state.reactionOverlays[chatId]?.toMutableMap() ?: mutableMapOf()
                for (reactionUpdate in update.reactionUpdates) {
                    applyReactionUpdate(chatOverlays, reactionUpdate)
                }
                state.copy(
                    reactionOverlays = state.reactionOverlays + (chatId to chatOverlays.toMap())
                )
            }
        }

        // --- Eagerly update token balance + count receipts for analytics ---

        val selfId = userManager.accountId
        val countedThrough = metadataDataSource.getAnalyticsCountedThrough(chatId)
        var highestCounted = countedThrough

        for (msg in resolvedMessages) {
            if (msg.senderId == selfId) continue
            for (content in msg.content) {
                if (content is MessageContent.Cash) {
                    tokenCoordinator.add(content.mint, content.amount)
                }
            }

            // people.increment is cumulative and has no message identity, so a
            // replay would inflate the counter permanently. The watermark is the
            // only thing standing between gap fill and a corrupted profile.
            if (msg.messageId <= countedThrough) continue
            countReceipt(msg)
            highestCounted = maxOf(highestCounted, msg.messageId)
        }

        if (highestCounted > countedThrough) {
            metadataDataSource.advanceAnalyticsCountedThrough(chatId, highestCounted)
        }

        // --- Unknown chat → full feed sync ---

        if (lastMsg != null) {
            if (!metadataDataSource.exists(chatId)) {
                _events.send(Event.SyncFeedRequested)
            }
        }

        // --- Typing indicators ---

        if (update.typingNotifications.isNotEmpty()) {
            stateHolder.update { state ->
                val currentTypists = state.typingIndicators[chatId]?.toMutableSet() ?: mutableSetOf()
                for (notification in update.typingNotifications) {
                    applyTypingNotification(currentTypists, notification)
                }
                state.copy(
                    typingIndicators = state.typingIndicators + (chatId to currentTypists.toSet())
                )
            }
        }
    }

    private fun applyReactionUpdate(
        overlays: MutableMap<Long, ReactionSummary>,
        update: ReactionUpdate,
    ) {
        val existing = overlays[update.messageId]
        val existingReactions = existing?.reactions?.toMutableList() ?: mutableListOf()

        val idx = existingReactions.indexOfFirst { it.emoji == update.emoji }
        if (idx >= 0) {
            val current = existingReactions[idx]
            if (update.sequence <= current.sequence) return
            existingReactions[idx] = EmojiReaction(
                emoji = update.emoji,
                count = update.count,
                reactedBySelf = current.reactedBySelf,
                sampleReactors = current.sampleReactors,
                sequence = update.sequence,
            )
        } else {
            existingReactions.add(
                EmojiReaction(
                    emoji = update.emoji,
                    count = update.count,
                    reactedBySelf = false,
                    sampleReactors = emptyList(),
                    sequence = update.sequence,
                )
            )
        }

        existingReactions.removeAll { it.count <= 0 }

        overlays[update.messageId] = ReactionSummary(
            messageId = update.messageId,
            reactions = existingReactions.toList(),
        )
    }

    private fun scheduleGapFill(chatId: ChatId) {
        val scope = scope ?: return
        val job = scope.launch {
            delay(GAP_FILL_DELAY)
            if (!sequenceTracker.hasGap(chatId)) return@launch
            trace(tag = TAG, message = "Gap fill timeout: fetching delta for $chatId", type = TraceType.Process)
            performDeltaSync(chatId)
        }
        sequenceTracker.setGapFillJob(chatId, job)
    }

    private fun applyTypingNotification(
        typists: MutableSet<ActiveTypist>,
        notification: TypingNotification,
    ) {
        when (notification.state) {
            TypingState.STARTED_TYPING, TypingState.STILL_TYPING -> {
                typists.removeAll { it.userId == notification.userId }
                typists.add(ActiveTypist(userId = notification.userId, since = Clock.System.now()))
            }
            TypingState.STOPPED_TYPING, TypingState.TYPING_TIMED_OUT -> {
                typists.removeAll { it.userId == notification.userId }
            }
            TypingState.UNKNOWN -> Unit
        }
    }

    // endregion
}
