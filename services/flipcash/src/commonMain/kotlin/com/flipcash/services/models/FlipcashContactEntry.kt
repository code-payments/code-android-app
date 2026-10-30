package com.flipcash.services.models

import com.flipcash.services.models.chat.ChatId
import kotlin.time.Instant

data class FlipcashContactEntry(
    val phoneNumber: String,
    val dmChatId: ChatId?,
    val joinedAt: Instant,
)
