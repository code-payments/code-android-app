package com.flipcash.shared.chat

import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.getcode.opencode.model.core.ID
import kotlin.time.Instant

data class ChatState(
    val feed: List<ChatMetadata> = emptyList(),
    val typingIndicators: Map<ChatId, Set<ActiveTypist>> = emptyMap(),
    val feedSyncState: FeedSyncState = FeedSyncState.Idle,
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
