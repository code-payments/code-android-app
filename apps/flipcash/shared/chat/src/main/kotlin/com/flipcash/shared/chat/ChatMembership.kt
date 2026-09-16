package com.flipcash.shared.chat

import com.flipcash.services.models.chat.ChatMetadata

/**
 * A chat's metadata together with whether this device is a member of it.
 *
 * [ChatMetadata] is server truth about the chat; membership is a fact about the viewer, stored on
 * the same row and written by a join, a leave or a feed sync. The access gate needs both to decide
 * what it renders, and a caller that had to ask twice could observe them a frame apart and blur a
 * chat it had just joined.
 */
data class ChatMembership(
    val metadata: ChatMetadata,
    val isMember: Boolean,
)
