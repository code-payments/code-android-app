package com.flipcash.shared.chat

import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ReactionSummary
import com.getcode.opencode.model.core.ID
import kotlin.time.Instant

data class ChatState(
    val feed: List<ChatMetadata> = emptyList(),
    val typingIndicators: Map<ChatId, Set<ActiveTypist>> = emptyMap(),
    val reactionOverlays: Map<ChatId, Map<Long, ReactionSummary>> = emptyMap(),
    val feedSyncState: FeedSyncState = FeedSyncState.Idle,
    val historyHydration: ChatHydrationState = ChatHydrationState.Unknown,
    val activeChat: ChatId? = null,
)

data class ChatSummary(
    val metadata: ChatMetadata,
    val unreadCount: Int,
)

data class ActiveTypist(
    val userId: ID,
    val since: Instant,
)

enum class FeedSyncState {
    Idle,
    Syncing,
    Synced,
    Error,
}

/**
 * How far the *message* cache has got in reconciling itself with the server for the signed-in user.
 *
 * Distinct from [FeedSyncState], which reports only the conversation-list fetch. That fetch marks
 * itself [FeedSyncState.Synced] as soon as the metadata lands, before the per-chat history backfill
 * it schedules has run — so it cannot answer "is this account's chat history here yet". Callers that
 * read chat history as evidence need that second question: the wallet's "send a tip" milestone looks
 * for an outgoing TIPPED message, and an absent one only means the user has never tipped once the
 * cache is known to be complete.
 */
enum class ChatHydrationState {
    /** No sync has finished a full pass this session — an absent message proves nothing. */
    Unknown,

    /** A sync succeeded and every catch-up it scheduled has run: an absent message really is absent. */
    Hydrated,

    /** A sync completed without success. Callers should stop waiting; the next sync will retry. */
    Unavailable,
}
