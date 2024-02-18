package com.getcode.model.notifications

import com.getcode.model.MessageContent

data class CodeNotification(
    val type: String,
    val title: String,
    val body: MessageContent,
)