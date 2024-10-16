package com.getcode.model.chat

enum class ChatType {
    Unknown,
    TwoWay;

    companion object {
        val types = entries
    }
}