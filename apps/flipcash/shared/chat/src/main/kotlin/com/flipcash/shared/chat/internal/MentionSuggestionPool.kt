package com.flipcash.shared.chat.internal

import com.flipcash.app.persistence.sources.UserProfileDataSource
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.MentionSuggestion
import com.getcode.opencode.model.core.ID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The pool of people a group offers for `@` mentions, as `Chat.GetMentionSuggestions` ranked it.
 *
 * Fetched once per composing session: [startSession] replaces whatever is held with a new fetch,
 * and [pool] waits on that fetch, starting one if no session has begun. Held in memory only; the
 * next session fetches again, which is how senders the pool has never seen get in.
 *
 * Between fetches, [onMessages] keeps the order fresh by moving a known sender to the front.
 */
@Singleton
internal class MentionSuggestionPool @Inject constructor(
    private val chatController: ChatController,
    private val profiles: UserProfileDataSource,
) : MentionPoolUpdates {

    private val lock = Mutex()
    private val sessions = mutableMapOf<ChatId, Deferred<Result<List<MentionSuggestion>>>>()

    /** Begins a composing session for [chatId] with a fresh fetch, on [scope]. */
    suspend fun startSession(scope: CoroutineScope, chatId: ChatId) {
        lock.withLock { sessions[chatId] = scope.fetchAsync(chatId) }
    }

    /** [chatId]'s pool for the current session, most relevant first, or the failure that ended its fetch. */
    suspend fun pool(scope: CoroutineScope, chatId: ChatId): Result<List<MentionSuggestion>> =
        lock.withLock { sessions.getOrPut(chatId) { scope.fetchAsync(chatId) } }.await()

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun onMessages(chatId: ChatId, senders: List<Pair<ID, Instant>>) {
        lock.withLock {
            val session = sessions[chatId] ?: return
            if (!session.isCompleted) return
            val held = session.getCompleted().getOrNull() ?: return
            sessions[chatId] = CompletableDeferred(Result.success(movedToFront(held, senders)))
        }
    }

    private fun CoroutineScope.fetchAsync(chatId: ChatId): Deferred<Result<List<MentionSuggestion>>> = async {
        chatController.getMentionSuggestions(chatId).onSuccess { pool ->
            pool.forEach { suggestion ->
                val userId = suggestion.userProfile.userId ?: return@forEach
                profiles.store(userId, suggestion.userProfile)
            }
        }
    }

}

/** Told about each message the event stream delivers, so the mention pool's order stays fresh. */
interface MentionPoolUpdates {
    suspend fun onMessages(chatId: ChatId, senders: List<Pair<ID, Instant>>)

    object None : MentionPoolUpdates {
        override suspend fun onMessages(chatId: ChatId, senders: List<Pair<ID, Instant>>) = Unit
    }
}

/**
 * [pool] with each sender in [senders] that it already holds moved to the front, newest message
 * first, when that message is newer than the sender's last_sent_at. A sender the pool doesn't hold
 * is left out; the next session's fetch brings them in.
 */
internal fun movedToFront(
    pool: List<MentionSuggestion>,
    senders: List<Pair<ID, Instant>>,
): List<MentionSuggestion> {
    val byUser = pool.associateBy { it.userProfile.userId }
    val latest = senders.groupBy({ it.first }, { it.second }).mapValues { (_, times) -> times.max() }
    val moved = latest
        .mapNotNull { (userId, sentAt) ->
            val held = byUser[userId] ?: return@mapNotNull null
            val last = held.lastSentAt
            if (last != null && last >= sentAt) null else held.copy(lastSentAt = sentAt)
        }
        .sortedByDescending { it.lastSentAt }
    if (moved.isEmpty()) return pool
    val movedIds = moved.map { it.userProfile.userId }.toSet()
    return moved + pool.filterNot { it.userProfile.userId in movedIds }
}
