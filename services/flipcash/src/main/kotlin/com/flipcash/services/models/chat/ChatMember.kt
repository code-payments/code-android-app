package com.flipcash.services.models.chat

import com.flipcash.services.models.UserProfile
import com.getcode.opencode.model.core.ID

data class ChatMember(
    val userId: ID,
    val userProfile: UserProfile,
    val pointers: List<MessagePointer>,
)
