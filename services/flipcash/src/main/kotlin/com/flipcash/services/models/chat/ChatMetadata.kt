package com.flipcash.services.models.chat

import com.getcode.opencode.model.core.ID
import kotlin.time.Instant

data class ChatMetadata(
    val chatId: ChatId,
    val type: ChatType,
    val members: List<ChatMember>,
    val lastMessage: ChatMessage?,
    val lastActivity: Instant,
    val latestEventSequence: Long = 0,
    val isHidden: Boolean = false,
    // Title for this chat. Only set for group chats.
    val title: String? = null,
    // Picture for this chat. Only set for group chats.
    val picture: MediaItem? = null,
    // True roster size and staleness version. Server-authoritative; defaults to zero for
    // metadata reconstructed without a server round trip.
    val rosterSummary: RosterSummary = RosterSummary(memberCount = 0, version = 0),
    // Participation requirements for this chat. Only set for group chats; null means the chat
    // has no requirements.
    val rules: ChatRules? = null,
    // Per-viewer chat state, absent when the chat holds nothing about the viewer.
    val viewerState: ViewerState? = null,
    // Group creator; null for DMs and for group chats reconstructed without a server round trip.
    val creator: ID? = null,
    // Transitional E2EE flag (DMs only): true means clients should send new content as
    // EncryptedContent. Ignored behaviourally for now -- see chat/v1 model.proto.
    val useE2ee: Boolean = false,
)
