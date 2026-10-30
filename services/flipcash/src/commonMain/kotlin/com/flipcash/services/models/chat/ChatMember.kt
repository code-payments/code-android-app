package com.flipcash.services.models.chat

import com.flipcash.services.models.UserProfile
import com.getcode.opencode.model.core.ID
import kotlin.time.Instant

data class ChatMember(
    val userId: ID,
    val userProfile: UserProfile,
    val pointers: List<MessagePointer>,
    // When this member joined, if known. Set wherever version is; unset (null) for DM
    // participants and on Metadata.members (the viewer's own entry only).
    val joinedAt: Instant? = null,
    // Merge key for a roster fetched in pages: greater wins. Zero for chat-creation joins
    // and all DM participants, since neither carries a meaningful join order.
    val version: Long = 0,
)
