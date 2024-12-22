package com.getcode.model.chat

import com.getcode.model.ID

data class Sender(
    val id: ID? = null,
    val profileImage: String? = null,
    val displayName: String? = null,
    val isHost: Boolean = false,
    val isSelf: Boolean = false,
    val isBlocked: Boolean = false,
)
