package com.flipcash.services.models

import com.flipcash.services.models.chat.ChatId

data class FlipcashContactEntry(
    val phoneNumber: String,
    val dmChatId: ChatId?,
)
