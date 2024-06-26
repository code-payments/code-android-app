package com.getcode.model.chat

sealed interface ConversationType {
    data object TipChat: ConversationType
}