package com.getcode.model.chat

enum class ChatType {
    Unknown,
    TwoWay,
    GroupChat;

    companion object {
        val types = entries
    }
}