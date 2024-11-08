package com.getcode.model.chat

import com.getcode.model.ID

data class Sender(
    val id: ID?,
    val profileImage: String?,
    val displayName: String?,
    val isHost: Boolean,
    val isSelf: Boolean
)
