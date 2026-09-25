package com.flipcash.shared.chat.internal.delegates

import com.flipcash.app.persistence.sources.ChatMessageDataSource
import com.flipcash.services.controllers.ChatMessagingController
import com.flipcash.services.models.AddReactionError
import com.flipcash.services.models.PagingToken
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.RemoveReactionError
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.Emoji
import com.flipcash.services.models.chat.EmojiReaction
import com.flipcash.services.models.chat.ReactionSummary
import com.flipcash.services.models.chat.Reactor
import com.flipcash.services.repository.ReactorsPage
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.MessageReactions
import com.flipcash.shared.chat.ReactionOperations
import com.flipcash.shared.chat.internal.ChatStateHolder
import com.flipcash.shared.chat.reactions.ReactionCall
import com.flipcash.shared.chat.reactions.ReactionError
import com.flipcash.shared.chat.reactions.ReactionFailure
import com.flipcash.shared.chat.reactions.ReactionResult
import com.flipcash.shared.chat.reactions.ReactionState
import com.getcode.libs.emojis.reactions.RecentReactionsStore
import com.getcode.opencode.model.core.ID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock

/**
 * Per-message reaction taps and lookups: toggling, refreshing confirmed state from the server, and
 * paging the "who reacted" sheet.
 *
 * Holds one [ReactionState] per message this device has looked at — [snapshot] creates it lazily
 * and folds in whatever [ChatStateHolder] currently has confirmed for that message (kept current by
 * [EventStreamDelegate]'s own stream-driven reducer, and by [refreshReactions]'s explicit fetch).
 * Only that fold is ever repeated; [ReactionState.applySummary] accepts by version, so replaying the
 * same or older confirmed state over a pending tap never disturbs it. This mirrors the iOS
 * reference (`ConversationReactions`): pending/in-flight state stays in memory only, and only the
 * confirmed side is persisted, via [ChatMessageDataSource.mergeReactions].
 *
 * @see com.flipcash.shared.chat.internal.RealChatCoordinator
 */
@Singleton
class ReactionsDelegate @Inject constructor(
    private val messagingController: ChatMessagingController,
    private val messageDataSource: ChatMessageDataSource,
    private val recentReactionsStore: RecentReactionsStore,
    private val userManager: UserManager,
    private val stateHolder: ChatStateHolder,
) : ReactionOperations {

    /** One [ReactionState] per message this device has looked at, keyed by chat then message id. */
    private val liveStates: MutableMap<ChatId, MutableMap<Long, ReactionState>> = mutableMapOf()

    /**
     * Bumped on every local tap/respond so [observeChatReactions] re-emits — a pending tap or an
     * in-flight call changes [liveStates] without touching [ChatStateHolder.state], which is the
     * only thing [observeChatReactions] would otherwise be reacting to.
     */
    private val localVersion = MutableStateFlow(0)

    private val _reactionErrors = MutableSharedFlow<ReactionError>(extraBufferCapacity = 1)
    override val reactionErrors: SharedFlow<ReactionError> = _reactionErrors.asSharedFlow()

    override fun observeChatReactions(chatId: ChatId): Flow<Map<Long, MessageReactions>> =
        combine(stateHolder.state, localVersion) { state, _ ->
            val overlayIds = state.reactionOverlays[chatId]?.keys.orEmpty()
            val liveIds = liveStates[chatId]?.keys.orEmpty()
            val ids = overlayIds + liveIds
            val result = mutableMapOf<Long, MessageReactions>()
            for (messageId in ids) {
                val reactionState = snapshot(chatId, messageId)
                result[messageId] = MessageReactions(pills = reactionState.pills, selfReactions = reactionState.selfReactions)
            }
            result
        }.distinctUntilChanged()

    override suspend fun toggleReaction(chatId: ChatId, messageId: Long, emoji: String) {
        val state = snapshot(chatId, messageId)
        val call = state.tap(emoji, Clock.System.now())
        localVersion.update { it + 1 }
        if (call != null) sendCall(chatId, messageId, state, call)
    }

    override suspend fun refreshReactions(chatId: ChatId, messageIds: List<Long>): Result<Unit> {
        val summaries = messagingController.getReactionSummariesByIds(chatId, messageIds)
            .getOrElse { return Result.failure(it) }
        for (summary in summaries) {
            val state = snapshot(chatId, summary.messageId)
            state.applySummary(summary.reactions.map { it.toSummaryEntry() })
            persist(chatId, summary.messageId, state)
        }
        localVersion.update { it + 1 }
        return Result.success(Unit)
    }

    override suspend fun getReactorsPage(
        chatId: ChatId,
        messageId: Long,
        emoji: String,
        token: PagingToken?,
    ): Result<ReactorsPage> =
        messagingController.getReactors(chatId, messageId, Emoji(emoji), QueryOptions(token = token))

    /** Drops all in-memory reaction state (pending taps, in-flight calls, confirmed cache). */
    fun clearAll() {
        liveStates.clear()
        localVersion.value = 0
    }

    /**
     * Sends [call], settles it against [state], persists confirmed state on success, records a
     * successful add to the recents ranking, reports a non-silent failure on [reactionErrors], and
     * loops once more if settling produced a coalesced follow-up call — the same shape as the iOS
     * reference's tap → send → respond cycle.
     */
    private suspend fun sendCall(chatId: ChatId, messageId: Long, state: ReactionState, call: ReactionCall) {
        val result = performCall(chatId, messageId, call)
        val (followUp, error) = state.respond(call.emoji, result)
        if (result is ReactionResult.Ok) {
            persist(chatId, messageId, state)
            if (call.op == ReactionCall.Op.ADD) recentReactionsStore.record(call.emoji)
        }
        localVersion.update { it + 1 }
        error?.let { _reactionErrors.emit(it) }
        if (followUp != null) sendCall(chatId, messageId, state, followUp)
    }

    private suspend fun performCall(chatId: ChatId, messageId: Long, call: ReactionCall): ReactionResult {
        val emoji = Emoji(call.emoji)
        val result = when (call.op) {
            ReactionCall.Op.ADD -> messagingController.addReaction(chatId, messageId, emoji)
            ReactionCall.Op.REMOVE -> messagingController.removeReaction(chatId, messageId, emoji)
        }
        return result.fold(
            onSuccess = { reaction ->
                ReactionResult.Ok(
                    count = reaction.count,
                    selfReacted = reaction.selfReactor != null,
                    version = reaction.version,
                    selfReactedAt = reaction.selfReactor?.reactedAt,
                )
            },
            onFailure = { error -> ReactionResult.Failed(failureOf(call.op, error)) },
        )
    }

    private fun failureOf(op: ReactionCall.Op, error: Throwable): ReactionFailure = when (op) {
        ReactionCall.Op.ADD -> when (error) {
            is AddReactionError.Denied -> ReactionFailure.DENIED
            is AddReactionError.MessageNotFound -> ReactionFailure.MESSAGE_NOT_FOUND
            is AddReactionError.CannotReact -> ReactionFailure.CANNOT_REACT
            is AddReactionError.TooManyReactionTypes -> ReactionFailure.TOO_MANY_REACTION_TYPES
            else -> ReactionFailure.NETWORK
        }
        ReactionCall.Op.REMOVE -> when (error) {
            is RemoveReactionError.Denied -> ReactionFailure.DENIED
            is RemoveReactionError.MessageNotFound -> ReactionFailure.MESSAGE_NOT_FOUND
            else -> ReactionFailure.NETWORK
        }
    }

    /**
     * Gets or creates the message's [ReactionState] and folds in the latest confirmed overlay.
     *
     * A freshly-created state is first seeded from Room: after a relaunch the confirmed reactions
     * for a message this device already knew about live only on disk, not in [ChatStateHolder] —
     * without this, the first tap on an emoji the user already reacted with would compute an ADD
     * instead of a REMOVE. Only done once per message (the `getOrPut` below only runs the seed
     * block when creating), since after that [ChatStateHolder]'s overlay (kept current by
     * [EventStreamDelegate] and [refreshReactions]) is the live source of truth.
     */
    private suspend fun snapshot(chatId: ChatId, messageId: Long): ReactionState {
        val chatStates = liveStates.getOrPut(chatId) { mutableMapOf() }
        val state = chatStates[messageId] ?: ReactionState().also { fresh ->
            messageDataSource.getMessage(chatId, messageId)?.reactions?.let { summary ->
                fresh.applySummary(summary.reactions.map { it.toSummaryEntry() })
            }
            chatStates[messageId] = fresh
        }
        stateHolder.current.reactionOverlays[chatId]?.get(messageId)?.let { summary ->
            state.applySummary(summary.reactions.map { it.toSummaryEntry() })
        }
        return state
    }

    /** Persists [state]'s confirmed side and mirrors it into [ChatStateHolder.state]'s overlay. */
    private suspend fun persist(chatId: ChatId, messageId: Long, state: ReactionState) {
        val selfId = userManager.accountId
        val summary = ReactionSummary(
            messageId = messageId,
            reactions = state.summaryEntries.map { it.toEmojiReaction(selfId) },
        )
        messageDataSource.mergeReactions(chatId, messageId, summary)
        stateHolder.update { current ->
            val chatOverlays = (current.reactionOverlays[chatId] ?: emptyMap()) + (messageId to summary)
            current.copy(reactionOverlays = current.reactionOverlays + (chatId to chatOverlays))
        }
    }

    private fun EmojiReaction.toSummaryEntry(): ReactionState.SummaryEntry = ReactionState.SummaryEntry(
        emoji = emoji.value,
        count = count,
        selfReacted = selfReactor != null,
        version = version,
        selfReactedAt = selfReactor?.reactedAt,
    )

    private fun ReactionState.SummaryEntry.toEmojiReaction(selfId: ID?): EmojiReaction = EmojiReaction(
        emoji = Emoji(emoji),
        count = count,
        selfReactor = if (selfReacted && selfId != null) {
            Reactor(userId = selfId, reactedAt = selfReactedAt ?: Clock.System.now(), version = version)
        } else {
            null
        },
        sampleReactors = emptyList(),
        version = version,
    )
}
